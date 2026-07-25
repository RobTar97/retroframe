package com.rober.photoframe.model

import android.net.Uri

enum class MediaType {
    IMAGE,
    VIDEO,
}

/**
 * A single photo or video discovered in the user's chosen folder.
 *
 * [documentId] is the Storage Access Framework's own identifier for the file. It is stable
 * and unique within a provider, which makes it a far better identity than the filename hash
 * this class used to key on — two differently named files could collide there.
 */
data class MediaItem(
    val documentId: String,
    val uri: Uri,
    val name: String,
    val type: MediaType,
    val dateModified: Long,
    val size: Long,
) {
    val isVideo: Boolean get() = type == MediaType.VIDEO
}
