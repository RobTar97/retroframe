package com.rober.photoframe.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.rober.photoframe.data.PhotoRepository
import com.rober.photoframe.model.MediaItem
import com.rober.photoframe.settings.PhotoframePreferences
import com.rober.photoframe.util.DirectoryWatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SlideshowViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PhotoRepository(application)
    private val directoryWatcher = DirectoryWatcher(application)
    
    private val _mediaItems = MutableLiveData<List<MediaItem>>()
    val mediaItems: LiveData<List<MediaItem>> = _mediaItems

    private val _currentPosition = MutableLiveData<Int>()
    val currentPosition: LiveData<Int> = _currentPosition

    private val _isSlideshowRunning = MutableLiveData<Boolean>(true)
    val isSlideshowRunning: LiveData<Boolean> = _isSlideshowRunning

    private var slideshowJob: Job? = null

    init {
        loadMedia()
        startDirectoryWatcher()
    }



    private fun loadMedia() {
        viewModelScope.launch {
            val items = repository.loadMedia()
            _mediaItems.value = items
            if (items.isNotEmpty()) {
                startSlideshow()
            }
        }
    }

    private fun startDirectoryWatcher() {
        directoryWatcher.startMonitoring(viewModelScope)
        viewModelScope.launch {
            directoryWatcher.fileChanges.collectLatest {
                loadMedia()
            }
        }
    }

    fun startSlideshow(restart: Boolean = false) {
        _isSlideshowRunning.value = true
        if (restart) {
            slideshowJob?.cancel()
        } else if (slideshowJob?.isActive == true) {
            return // Already running
        }
        
        slideshowJob = viewModelScope.launch {
            while (true) {
                delay(PhotoframePreferences.slideIntervalSeconds * 1000L)
                if (_isSlideshowRunning.value == true) {
                    val current = _currentPosition.value ?: 0
                    val count = _mediaItems.value?.size ?: 0
                    if (count > 0) {
                        _currentPosition.value = (current + 1) % count
                    }
                }
            }
        }
    }

    fun updateCurrentPosition(position: Int) {
        _currentPosition.value = position
        // Restart timer to avoid immediate auto-advance after manual swipe
        if (_isSlideshowRunning.value == true) {
            startSlideshow(restart = true)
        }
    }

    fun stopSlideshow() {
        _isSlideshowRunning.value = false
        slideshowJob?.cancel()
    }
    
    fun toggleSlideshow() {
        if (_isSlideshowRunning.value == true) {
            stopSlideshow()
        } else {
            startSlideshow(restart = true)
        }
    }

    fun refreshMedia() {
        loadMedia()
    }

    override fun onCleared() {
        super.onCleared()
        directoryWatcher.stopMonitoring()
    }
}
