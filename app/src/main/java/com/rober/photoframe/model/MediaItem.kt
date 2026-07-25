package com.rober.photoframe.model

import android.net.Uri

enum class MediaType {
    IMAGE, VIDEO
}

data class MediaItem(
    val id: Long,
    val uri: Uri,
    val name: String,
    val type: MediaType,
    val dateModified: Long
)
