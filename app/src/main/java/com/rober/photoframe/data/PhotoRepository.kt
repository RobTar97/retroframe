package com.rober.photoframe.data

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import com.rober.photoframe.model.MediaItem
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/**
 * Reads the user's chosen folder and returns the media inside it.
 *
 * ## Why this does not use DocumentFile
 *
 * The obvious implementation is `DocumentFile.fromTreeUri(...).listFiles()`, and that is
 * what RetroFrame originally did. It is dramatically slower than it looks.
 *
 * `listFiles()` runs one cursor query to get the child document IDs, then wraps each result
 * in a `DocumentFile`. Every subsequent property read — `name`, `lastModified()`, `isDirectory`
 * — issues *another* query across a Binder boundary into the storage provider. Scanning a
 * folder of 500 photos therefore costs roughly 1500 IPC round trips.
 *
 * On the single-core tablets this app targets, that was the single most expensive thing the
 * app did, and it ran on a timer.
 *
 * This implementation asks for every column it needs in one query and reads them straight
 * off the cursor: one IPC round trip for the whole folder, regardless of size.
 */
class PhotoRepository(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    private companion object {
        const val TAG = "PhotoRepository"

        val PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_SIZE,
        )
    }

    /**
     * Scans [treeUri] and returns everything displayable inside it, unsorted and unweighted.
     * Ordering is the playlist layer's job — see [PlaylistBuilder].
     *
     * Returns an empty list rather than throwing if the folder is unreadable: a photo frame
     * that has been running for weeks should degrade to showing nothing, not crash, when an
     * SD card is removed.
     */
    suspend fun scan(treeUri: Uri, includeVideos: Boolean): List<MediaItem> =
        withContext(ioDispatcher) {
            val childrenUri = try {
                DocumentsContract.buildChildDocumentsUriUsingTree(
                    treeUri,
                    DocumentsContract.getTreeDocumentId(treeUri),
                )
            } catch (e: IllegalArgumentException) {
                // Saved URI is from an older install, a different provider, or is malformed.
                Log.e(TAG, "Not a usable tree URI: $treeUri", e)
                return@withContext emptyList()
            }

            val items = ArrayList<MediaItem>(256)

            try {
                queryChildren(childrenUri)?.use { cursor ->
                    val idColumn = cursor.getColumnIndexOrThrow(PROJECTION[0])
                    val nameColumn = cursor.getColumnIndexOrThrow(PROJECTION[1])
                    val mimeColumn = cursor.getColumnIndexOrThrow(PROJECTION[2])
                    val modifiedColumn = cursor.getColumnIndex(PROJECTION[3])
                    val sizeColumn = cursor.getColumnIndex(PROJECTION[4])

                    while (cursor.moveToNext()) {
                        // A folder scan can outlive the screen it was started for.
                        coroutineContext.ensureActive()

                        val mimeType = cursor.getStringOrNull(mimeColumn)
                        if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) continue

                        val documentId = cursor.getStringOrNull(idColumn) ?: continue
                        val name = cursor.getStringOrNull(nameColumn) ?: continue

                        val type = MediaTypes.classify(mimeType, name) ?: continue
                        if (type == com.rober.photoframe.model.MediaType.VIDEO && !includeVideos) {
                            continue
                        }

                        items += MediaItem(
                            documentId = documentId,
                            uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId),
                            name = name,
                            type = type,
                            dateModified = modifiedColumn.takeIf { it >= 0 }
                                ?.let { cursor.getLong(it) } ?: 0L,
                            size = sizeColumn.takeIf { it >= 0 }
                                ?.let { cursor.getLong(it) } ?: 0L,
                        )
                    }
                }
            } catch (e: SecurityException) {
                // Persisted permission was revoked, or the volume was unmounted.
                Log.e(TAG, "Lost permission for $treeUri", e)
                return@withContext emptyList()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to scan $treeUri", e)
                return@withContext emptyList()
            }

            Log.d(TAG, "Scanned ${items.size} items from $treeUri")
            items
        }

    /**
     * Cheap change-detection signature for the folder. Used by [FolderMonitor]'s safety-net
     * poll to decide whether anything actually changed, without building the full media list.
     */
    suspend fun signature(treeUri: Uri): FolderSignature = withContext(ioDispatcher) {
        val childrenUri = try {
            DocumentsContract.buildChildDocumentsUriUsingTree(
                treeUri,
                DocumentsContract.getTreeDocumentId(treeUri),
            )
        } catch (e: IllegalArgumentException) {
            return@withContext FolderSignature.UNKNOWN
        }

        var count = 0
        var modifiedSum = 0L

        try {
            queryChildren(childrenUri)?.use { cursor ->
                val modifiedColumn = cursor.getColumnIndex(PROJECTION[3])
                while (cursor.moveToNext()) {
                    coroutineContext.ensureActive()
                    count++
                    if (modifiedColumn >= 0) modifiedSum += cursor.getLong(modifiedColumn)
                }
            } ?: return@withContext FolderSignature.UNKNOWN
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read folder signature", e)
            return@withContext FolderSignature.UNKNOWN
        }

        FolderSignature(count, modifiedSum)
    }

    private fun queryChildren(childrenUri: Uri): Cursor? =
        context.contentResolver.query(childrenUri, PROJECTION, null, null, null)

    private fun Cursor.getStringOrNull(column: Int): String? =
        if (column >= 0 && !isNull(column)) getString(column) else null
}

/** Cheap fingerprint of a folder's contents. */
data class FolderSignature(val fileCount: Int, val modifiedSum: Long) {
    companion object {
        /** Returned when the folder could not be read; never compares equal to itself. */
        val UNKNOWN = FolderSignature(-1, -1)
    }
}
