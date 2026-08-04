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

        /**
         * How far below the chosen folder the scan will go.
         *
         * Deep enough for `Photos/2019/Italy/Rome`, shallow enough that pointing the app at a
         * whole SD card does not turn one folder pick into a thousand queries.
         */
        const val MAX_DEPTH = 5

        /**
         * Hard ceiling on how much is loaded into memory.
         *
         * The target device has 1 GB of RAM. Ten thousand [MediaItem]s is already a few MB of
         * strings and URIs, and no one curates a frame with more photos than that.
         */
        const val MAX_ITEMS = 10_000

        val PROJECTION =
            arrayOf(
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
     * With [includeSubfolders] the scan descends breadth-first, one query per directory. That
     * is only affordable because of the single-cursor design above: the old `DocumentFile`
     * approach would have made recursion cost hundreds of IPC round trips per subfolder.
     *
     * Returns an empty list rather than throwing if the folder is unreadable: a photo frame
     * that has been running for weeks should degrade to showing nothing, not crash, when an
     * SD card is removed.
     */
    suspend fun scan(
        treeUri: Uri,
        includeVideos: Boolean,
        includeSubfolders: Boolean = false,
    ): List<MediaItem> =
        withContext(ioDispatcher) {
            val root =
                rootDocumentId(treeUri) ?: return@withContext emptyList()

            val items = ArrayList<MediaItem>(256)

            walk(treeUri, root, includeSubfolders) { cursor, columns ->
                val mimeType = cursor.getStringOrNull(columns.mime)
                val documentId = cursor.getStringOrNull(columns.id) ?: return@walk true

                // Directories are the walk's business, not ours.
                if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) return@walk true

                val name = cursor.getStringOrNull(columns.name) ?: return@walk true
                val type = MediaTypes.classify(mimeType, name) ?: return@walk true
                if (type == com.rober.photoframe.model.MediaType.VIDEO && !includeVideos) {
                    return@walk true
                }

                items +=
                    MediaItem(
                        documentId = documentId,
                        uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId),
                        name = name,
                        type = type,
                        dateModified =
                            columns.modified.takeIf { it >= 0 }
                                ?.let { cursor.getLong(it) } ?: 0L,
                        size =
                            columns.size.takeIf { it >= 0 }
                                ?.let { cursor.getLong(it) } ?: 0L,
                    )

                // Stop the whole walk once the cap is reached.
                items.size < MAX_ITEMS
            }

            Log.d(TAG, "Scanned ${items.size} items from $treeUri")
            items
        }

    /**
     * Cheap change-detection signature for the folder. Used by [FolderMonitor]'s safety-net
     * poll to decide whether anything actually changed, without building the full media list.
     *
     * This has to cover exactly the same ground the scan does. A signature that only looked at
     * the top folder would go on reporting "nothing changed" forever while photos were added
     * to a subfolder — which is the failure the poll exists to catch.
     */
    suspend fun signature(
        treeUri: Uri,
        includeSubfolders: Boolean = false,
    ): FolderSignature =
        withContext(ioDispatcher) {
            val root = rootDocumentId(treeUri) ?: return@withContext FolderSignature.UNKNOWN

            var count = 0
            var modifiedSum = 0L

            val complete =
                walk(treeUri, root, includeSubfolders) { cursor, columns ->
                    count++
                    if (columns.modified >= 0) modifiedSum += cursor.getLong(columns.modified)
                    true
                }

            if (!complete) FolderSignature.UNKNOWN else FolderSignature(count, modifiedSum)
        }

    // ------------------------------------------------------------------ walking

    private fun rootDocumentId(treeUri: Uri): String? =
        try {
            DocumentsContract.getTreeDocumentId(treeUri)
        } catch (e: IllegalArgumentException) {
            // Saved URI is from an older install, a different provider, or is malformed.
            Log.e(TAG, "Not a usable tree URI: $treeUri", e)
            null
        }

    /** Column indices for one cursor, resolved once instead of per row. */
    private class Columns(cursor: Cursor) {
        val id = cursor.getColumnIndexOrThrow(PROJECTION[0])
        val name = cursor.getColumnIndexOrThrow(PROJECTION[1])
        val mime = cursor.getColumnIndexOrThrow(PROJECTION[2])
        val modified = cursor.getColumnIndex(PROJECTION[3])
        val size = cursor.getColumnIndex(PROJECTION[4])
    }

    /**
     * Breadth-first walk of the document tree, calling [onRow] for every child found.
     *
     * [onRow] returns false to abandon the walk. Directories are reported to it like any other
     * row; queueing them for descent is this function's job, so callers cannot forget a guard.
     *
     * @return true if the walk finished without an error.
     */
    private suspend fun walk(
        treeUri: Uri,
        rootDocumentId: String,
        includeSubfolders: Boolean,
        onRow: (Cursor, Columns) -> Boolean,
    ): Boolean {
        // A provider can report a folder as its own descendant — symlinked media directories
        // on old vendor ROMs do exactly this. MAX_DEPTH is what actually guarantees the walk
        // terminates; the visited set is what stops a cycle from being re-walked at every
        // remaining level, which would return the same photo several times over and turn a
        // handful of queries into an exponential number of them.
        val visited = HashSet<String>()
        var queue = ArrayDeque<String>()
        queue.addLast(rootDocumentId)
        visited += rootDocumentId

        var depth = 0
        while (queue.isNotEmpty() && depth <= MAX_DEPTH) {
            val next = ArrayDeque<String>()

            while (queue.isNotEmpty()) {
                val parentId = queue.removeFirst()
                val childrenUri =
                    try {
                        DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
                    } catch (e: IllegalArgumentException) {
                        continue
                    }

                try {
                    queryChildren(childrenUri)?.use { cursor ->
                        val columns = Columns(cursor)
                        while (cursor.moveToNext()) {
                            // A folder scan can outlive the screen it was started for.
                            coroutineContext.ensureActive()

                            val isDir =
                                cursor.getStringOrNull(columns.mime) ==
                                    DocumentsContract.Document.MIME_TYPE_DIR

                            if (!onRow(cursor, columns)) return true

                            if (isDir && includeSubfolders && depth < MAX_DEPTH) {
                                val childId = cursor.getStringOrNull(columns.id)
                                if (childId != null && visited.add(childId)) next.addLast(childId)
                            }
                        }
                    } ?: return false
                } catch (e: SecurityException) {
                    // Persisted permission was revoked, or the volume was unmounted.
                    Log.e(TAG, "Lost permission for $treeUri", e)
                    return false
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to read $parentId under $treeUri", e)
                    return false
                }
            }

            queue = next
            depth++
        }

        return true
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
