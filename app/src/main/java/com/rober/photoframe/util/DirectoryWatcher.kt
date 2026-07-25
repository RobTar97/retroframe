package com.rober.photoframe.util

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.rober.photoframe.settings.PhotoframePreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class DirectoryWatcher(private val context: Context) {

    private val TAG = "DirectoryWatcher"
    private var watcherJob: Job? = null
    private val _fileChanges = MutableSharedFlow<Unit>()
    val fileChanges: SharedFlow<Unit> = _fileChanges

    private var lastFileCount = -1
    private var lastModifiedSum: Long = -1

    fun startMonitoring(scope: CoroutineScope) {
        stopMonitoring()
        watcherJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                checkForChanges()
                delay(10000) // Check every 10 seconds
            }
        }
    }

    fun stopMonitoring() {
        watcherJob?.cancel()
        watcherJob = null
    }

    private suspend fun checkForChanges() {
        val uriString = PhotoframePreferences.galleryUriString ?: return
        try {
            val uri = Uri.parse(uriString)
            val directory = DocumentFile.fromTreeUri(context, uri) ?: return

            if (!directory.canRead()) return

            val files = directory.listFiles()
            val currentCount = files.size
            // Simple checksum using modification times
            val currentModSum = files.sumOf { it.lastModified() }

            if (lastFileCount != -1) {
                if (currentCount != lastFileCount || currentModSum != lastModifiedSum) {
                    Log.d(TAG, "Detected file changes")
                    _fileChanges.emit(Unit)
                }
            }

            lastFileCount = currentCount
            lastModifiedSum = currentModSum

        } catch (e: Exception) {
            Log.e(TAG, "Error checking for changes", e)
        }
    }
}
