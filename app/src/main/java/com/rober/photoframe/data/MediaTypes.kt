package com.rober.photoframe.data

import com.rober.photoframe.model.MediaType
import java.util.Locale

/**
 * Decides whether a file in the chosen folder is something RetroFrame can display.
 *
 * MIME type is trusted first, because the Storage Access Framework already reports it and
 * it is more reliable than a filename. Some older document providers report
 * `application/octet-stream` for everything, so the extension is used as a fallback rather
 * than as the primary signal.
 *
 * Pure functions with no Android dependencies, so they are directly unit testable.
 */
object MediaTypes {
    private val IMAGE_EXTENSIONS = setOf(
        "jpg", "jpeg", "png", "gif", "bmp", "webp", "heic", "heif",
    )

    private val VIDEO_EXTENSIONS = setOf(
        "mp4", "m4v", "mkv", "webm", "avi", "mov", "3gp",
    )

    /** Returns the media type, or null if this file should be skipped entirely. */
    fun classify(mimeType: String?, fileName: String?): MediaType? {
        fromMime(mimeType)?.let { return it }
        return fromExtension(fileName)
    }

    private fun fromMime(mimeType: String?): MediaType? {
        val mime = mimeType?.lowercase(Locale.ROOT) ?: return null
        return when {
            mime.startsWith("image/") -> MediaType.IMAGE
            mime.startsWith("video/") -> MediaType.VIDEO
            else -> null
        }
    }

    private fun fromExtension(fileName: String?): MediaType? {
        val name = fileName ?: return null
        val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        if (ext.isEmpty()) return null
        return when (ext) {
            in IMAGE_EXTENSIONS -> MediaType.IMAGE
            in VIDEO_EXTENSIONS -> MediaType.VIDEO
            else -> null
        }
    }
}
