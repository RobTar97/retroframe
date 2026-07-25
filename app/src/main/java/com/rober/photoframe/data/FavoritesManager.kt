package com.rober.photoframe.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages favorited photos using SharedPreferences
 * Favorites are identified by their URI strings
 */
object FavoritesManager {
    
    private const val PREFS_NAME = "photoframe_favorites"
    private const val KEY_FAVORITES = "favorite_uris"
    private const val DELIMITER = "|||" // Unique delimiter for URI separation
    
    private lateinit var prefs: SharedPreferences
    
    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    /**
     * Add a photo to favorites
     */
    fun addFavorite(uri: String) {
        val favorites = getAllFavorites().toMutableSet()
        favorites.add(uri)
        saveFavorites(favorites)
    }
    
    /**
     * Remove a photo from favorites
     */
    fun removeFavorite(uri: String) {
        val favorites = getAllFavorites().toMutableSet()
        favorites.remove(uri)
        saveFavorites(favorites)
    }
    
    /**
     * Toggle favorite status for a URI
     * @return true if now favorited, false if unfavorited
     */
    fun toggleFavorite(uri: String): Boolean {
        return if (isFavorite(uri)) {
            removeFavorite(uri)
            false
        } else {
            addFavorite(uri)
            true
        }
    }
    
    /**
     * Check if a URI is favorited
     */
    fun isFavorite(uri: String): Boolean {
        return getAllFavorites().contains(uri)
    }
    
    /**
     * Get all favorited URIs
     */
    fun getAllFavorites(): Set<String> {
        val favoritesString = prefs.getString(KEY_FAVORITES, "") ?: ""
        return if (favoritesString.isEmpty()) {
            emptySet()
        } else {
            favoritesString.split(DELIMITER).toSet()
        }
    }
    
    private fun saveFavorites(favorites: Set<String>) {
        val favoritesString = favorites.joinToString(DELIMITER)
        prefs.edit().putString(KEY_FAVORITES, favoritesString).apply()
    }
}
