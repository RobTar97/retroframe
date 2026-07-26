package com.rober.photoframe.data

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Notices when photos are added to or removed from the watched folder.
 *
 * ## Why this replaced polling
 *
 * The previous implementation listed the entire folder every 10 seconds, forever, which for
 * a photo frame means permanently. Combined with the old `DocumentFile`-based scan that was
 * thousands of Binder round trips per minute on a single-core tablet — the worst
 * performance defect in the app.
 *
 * This version registers a [ContentObserver] and does nothing at all until the storage
 * provider says something changed.
 *
 * A slow safety-net poll remains because not every document provider on old vendor Android
 * builds reliably fires change notifications. At [FALLBACK_POLL_MS] it is roughly 90× less
 * frequent than before, and it only compares a cheap [FolderSignature] rather than building
 * the full media list.
 */
class FolderMonitor(
    private val context: Context,
    private val repository: PhotoRepository,
) {
    private companion object {
        const val TAG = "FolderMonitor"

        /** Collapses bursts of notifications — copying 200 photos fires many events. */
        const val DEBOUNCE_MS = 2_000L

        /** Safety net for providers that do not notify. 15 minutes. */
        const val FALLBACK_POLL_MS = 15 * 60 * 1000L
    }

    private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val changes: SharedFlow<Unit> = _changes

    private var observer: ContentObserver? = null
    private var fallbackJob: Job? = null
    private var debounceJob: Job? = null
    private var lastSignature: FolderSignature? = null

    /**
     * Starts watching [treeUri]. Safe to call repeatedly; the previous watch is torn down
     * first. [scope] should be tied to the UI lifecycle so watching stops when the
     * slideshow is not on screen.
     */
    fun start(
        treeUri: Uri,
        scope: CoroutineScope,
    ) {
        stop()

        val childrenUri =
            try {
                DocumentsContract.buildChildDocumentsUriUsingTree(
                    treeUri,
                    DocumentsContract.getTreeDocumentId(treeUri),
                )
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "Cannot watch $treeUri", e)
                return
            }

        observer =
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) = onChange(selfChange, null)

                override fun onChange(
                    selfChange: Boolean,
                    uri: Uri?,
                ) {
                    Log.d(TAG, "Provider reported a change")
                    debounce(scope)
                }
            }.also { obs ->
                try {
                    context.contentResolver.registerContentObserver(childrenUri, true, obs)
                } catch (e: SecurityException) {
                    Log.e(TAG, "Not permitted to observe $treeUri", e)
                    observer = null
                }
            }

        fallbackJob =
            scope.launch {
                // Seed the signature so the first poll does not report a spurious change.
                lastSignature = repository.signature(treeUri)
                while (isActive) {
                    delay(FALLBACK_POLL_MS)
                    val current = repository.signature(treeUri)
                    if (current != FolderSignature.UNKNOWN && current != lastSignature) {
                        Log.d(TAG, "Fallback poll detected a change the provider did not report")
                        lastSignature = current
                        _changes.tryEmit(Unit)
                    }
                }
            }
    }

    fun stop() {
        observer?.let { context.contentResolver.unregisterContentObserver(it) }
        observer = null
        debounceJob?.cancel()
        debounceJob = null
        fallbackJob?.cancel()
        fallbackJob = null
    }

    private fun debounce(scope: CoroutineScope) {
        debounceJob?.cancel()
        debounceJob =
            scope.launch {
                delay(DEBOUNCE_MS)
                _changes.tryEmit(Unit)
            }
    }
}
