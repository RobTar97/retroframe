package com.rober.photoframe.data

/**
 * Works out a human-readable name for the chosen folder from its document ID alone.
 *
 * The alternative is to ask the storage provider for `COLUMN_DISPLAY_NAME`, which is a Binder
 * round trip — on the main thread, in a dialog, on a tablet where that is exactly the kind of
 * thing that produces a visible stall. It also fails outright once the grant is gone, which is
 * precisely when the user most wants to be told which folder the app thinks it is using.
 *
 * Parsing is best-effort by nature. Document IDs are opaque by contract; this understands the
 * shape used by Android's own storage provider (`primary:Pictures/Holidays`, `1A2B-3C4D:DCIM`)
 * and returns null rather than guessing when it sees something else useful-looking.
 */
object FolderNames {
    fun fromTreeDocumentId(documentId: String?): String? {
        if (documentId.isNullOrBlank()) return null

        // Everything before the first colon names the volume, not the folder.
        val path = documentId.substringAfter(':', "").trim('/')

        // No path means the grant covers a whole volume, which has no folder name to show.
        if (path.isEmpty()) return null

        val name = path.substringAfterLast('/')
        return name.takeIf { it.isNotEmpty() }
    }
}
