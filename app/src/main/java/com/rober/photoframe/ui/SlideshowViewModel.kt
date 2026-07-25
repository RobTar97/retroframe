package com.rober.photoframe.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.rober.photoframe.data.FavoritesManager
import com.rober.photoframe.data.PhotoRepository
import com.rober.photoframe.data.PlaylistBuilder
import com.rober.photoframe.model.MediaItem
import com.rober.photoframe.settings.PhotoframePreferences
import com.rober.photoframe.util.FolderMonitor
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** What the slideshow screen should currently be showing. */
sealed interface SlideshowState {
    data object Loading : SlideshowState
    data object NoFolderSelected : SlideshowState
    data object FolderEmpty : SlideshowState
    data class Ready(val playlist: List<MediaItem>) : SlideshowState
}

/**
 * Owns the media library, the playlist, and the advance timer.
 *
 * ## Advance timing
 *
 * The ViewModel does not own the current position — the ViewPager does. Instead it emits an
 * [advanceRequests] tick when the current slide has been shown long enough, and the fragment
 * moves the pager.
 *
 * This removes a feedback loop in the previous design, where the ViewModel wrote a position,
 * the fragment observed it and moved the pager, the pager reported the page change back, and
 * that restarted the timer — meaning every automatic advance also cancelled and recreated the
 * coroutine that had just fired it.
 */
class SlideshowViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PhotoRepository(application)
    private val folderMonitor = FolderMonitor(application, repository)

    private val _state = MutableLiveData<SlideshowState>(SlideshowState.Loading)
    val state: LiveData<SlideshowState> = _state

    private val _isPlaying = MutableLiveData(true)
    val isPlaying: LiveData<Boolean> = _isPlaying

    /**
     * Emitted when the pager should move to the next slide. Conflated: if the UI is briefly
     * unable to keep up there is no value in queuing several advances.
     */
    private val _advanceRequests = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val advanceRequests: SharedFlow<Unit> = _advanceRequests

    private var library: List<MediaItem> = emptyList()
    private var timerJob: Job? = null
    private var loadJob: Job? = null

    init {
        reload()
        observeFolder()
    }

    // ---------------------------------------------------------------- library

    fun reload() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val uriString = PhotoframePreferences.galleryUriString
            if (uriString.isNullOrEmpty()) {
                _state.value = SlideshowState.NoFolderSelected
                return@launch
            }

            val treeUri = Uri.parse(uriString)

            // The saved URI can outlive the grant that made it usable: a cloud restore onto
            // a new device, a permission revoked in system settings, or an SD card pulled
            // out. Without this check the app sits on an empty screen insisting it has a
            // folder; with it, the user is simply asked to pick one again.
            if (!hasPersistedAccess(treeUri)) {
                PhotoframePreferences.galleryUriString = null
                _state.value = SlideshowState.NoFolderSelected
                return@launch
            }
            library = repository.scan(treeUri, PhotoframePreferences.includeVideos)

            // Forget favourites whose photos are gone, so the set cannot grow without bound.
            FavoritesManager.retainOnly(library.mapTo(HashSet(library.size)) { it.documentId })

            _state.value = if (library.isEmpty()) {
                SlideshowState.FolderEmpty
            } else {
                SlideshowState.Ready(buildPlaylist())
            }

            if (library.isNotEmpty()) restartTimer()
        }
    }

    /** Rebuilds the playlist from the already-scanned library — no disk or IPC work. */
    private fun rebuildPlaylist() {
        if (library.isEmpty()) return
        _state.value = SlideshowState.Ready(buildPlaylist())
    }

    private fun buildPlaylist(): List<MediaItem> = PlaylistBuilder.build(
        library = library,
        favoriteIds = FavoritesManager.snapshot(),
        shuffle = PhotoframePreferences.shuffle,
    )

    private fun hasPersistedAccess(treeUri: Uri): Boolean =
        getApplication<Application>().contentResolver.persistedUriPermissions.any {
            it.uri == treeUri && it.isReadPermission
        }

    private fun observeFolder() {
        viewModelScope.launch {
            folderMonitor.changes.collect { reload() }
        }
    }

    fun startWatching() {
        val uriString = PhotoframePreferences.galleryUriString ?: return
        folderMonitor.start(Uri.parse(uriString), viewModelScope)
    }

    fun stopWatching() = folderMonitor.stop()

    // ---------------------------------------------------------------- favourites

    /**
     * @return true if the item is now favourited.
     *
     * Note that this deliberately does *not* reload or reshuffle. The old behaviour rescanned
     * the folder on every heart tap, which reshuffled the playlist and made the photo you had
     * just favourited vanish. The new weighting takes effect at the next natural reload.
     */
    fun toggleFavorite(item: MediaItem): Boolean = FavoritesManager.toggle(item.documentId)

    fun isFavorite(item: MediaItem): Boolean = FavoritesManager.isFavorite(item.documentId)

    // ---------------------------------------------------------------- playback

    /**
     * Called when a page becomes visible. Videos suspend the timer and advance when playback
     * ends instead, so a clip always plays to its natural end regardless of the interval.
     */
    fun onSlideShown(item: MediaItem?) {
        if (item?.isVideo == true) {
            stopTimer()
        } else if (_isPlaying.value == true) {
            restartTimer()
        }
    }

    fun togglePlayPause() {
        if (_isPlaying.value == true) {
            _isPlaying.value = false
            stopTimer()
        } else {
            _isPlaying.value = true
            restartTimer()
        }
    }

    /** Restarts the countdown, e.g. after the user manually swipes. */
    fun resetTimer() {
        if (_isPlaying.value == true) restartTimer()
    }

    private fun restartTimer() {
        stopTimer()
        val intervalMs = PhotoframePreferences.slideIntervalSeconds
            .coerceAtLeast(PhotoframePreferences.MIN_INTERVAL_SECONDS) * 1000L

        timerJob = viewModelScope.launch {
            while (isActive) {
                delay(intervalMs)
                if (_isPlaying.value != true) break
                _advanceRequests.tryEmit(Unit)
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    /** Settings changed: re-apply without a folder rescan unless the source itself changed. */
    fun onSettingsChanged(folderChanged: Boolean) {
        if (folderChanged) {
            reload()
            startWatching()
        } else {
            rebuildPlaylist()
            resetTimer()
        }
    }

    override fun onCleared() {
        super.onCleared()
        folderMonitor.stop()
        stopTimer()
    }
}
