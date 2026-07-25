package com.rober.photoframe.data

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.rober.photoframe.model.MediaItem
import com.rober.photoframe.model.MediaType
import com.rober.photoframe.settings.PhotoframePreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

class PhotoRepository(private val context: Context) {

    private val TAG = "PhotoRepository"

    suspend fun loadMedia(): List<MediaItem> = withContext(Dispatchers.IO) {
        val mediaList = mutableListOf<MediaItem>()
        val uriString = PhotoframePreferences.galleryUriString ?: return@withContext emptyList()
        
        try {
            val uri = Uri.parse(uriString)
            val directory = DocumentFile.fromTreeUri(context, uri)
            
            if (directory == null || !directory.canRead()) {
                Log.e(TAG, "Cannot read directory: $uriString")
                return@withContext emptyList()
            }

            val files = directory.listFiles()
            Log.d(TAG, "Found ${files.size} files in directory")

            for (file in files) {
                if (file.isDirectory) continue

                val name = file.name ?: continue
                val type = getMediaType(name) ?: continue

                // Filter out videos if disabled
                if (type == MediaType.VIDEO && !PhotoframePreferences.includeVideos) continue

                mediaList.add(
                    MediaItem(
                        id = name.hashCode().toLong(), // Simple ID generation
                        uri = file.uri,
                        name = name,
                        type = type,
                        dateModified = file.lastModified()
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading media", e)
        }

        // Sort by name or date if needed, currently just returning list
        // Could add sorting preference later
        return@withContext applyFavoriteWeighting(mediaList)
    }
    
    /**
     * Apply favorite weighting to media list
     * Favorited items appear 3x more often by being duplicated 2 additional times
     */
    private fun applyFavoriteWeighting(mediaList: List<MediaItem>): List<MediaItem> {
        val favoriteUris = FavoritesManager.getAllFavorites()
        if (favoriteUris.isEmpty()) {
            return mediaList.shuffled()
        }
        
        val weightedList = mutableListOf<MediaItem>()
        
        for (item in mediaList) {
            // Add original item
            weightedList.add(item)
            
            // If favorited, add 2 more copies for 3x weight
            if (favoriteUris.contains(item.uri.toString())) {
                weightedList.add(item)
                weightedList.add(item)
            }
        }
        
        return weightedList.shuffled()
    }

    private fun getMediaType(filename: String): MediaType? {
        val ext = filename.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return when (ext) {
            "jpg", "jpeg", "png", "gif", "bmp", "webp" -> MediaType.IMAGE
            "mp4", "mkv", "webm", "avi", "mov" -> MediaType.VIDEO
            else -> null
        }
    }
}
