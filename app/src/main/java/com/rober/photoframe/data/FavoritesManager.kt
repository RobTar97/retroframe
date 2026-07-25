package com.rober.photoframe.data

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri

/**
 * Tracks which photos the user has favourited.
 *
 * Favourites are keyed by Storage Access Framework document ID rather than by full URI.
 * Document IDs survive the folder being re-picked, which URIs do not — re-granting access
 * to the same folder used to silently lose every favourite.
 *
 * The set is held in memory because [isFavorite] is called on every page change. The old
 * implementation re-read and re-parsed a delimited string from disk each time.
 */
object FavoritesManager {

    private const val PREFS_NAME = "photoframe_favorites"
    private const val KEY_FAVORITES = "favorite_document_ids"

    /** Legacy key: a "|||"-delimited string of full URIs, migrated on first run. */
    private const val LEGACY_KEY = "favorite_uris"
    private const val LEGACY_DELIMITER = "|||"

    private lateinit var prefs: SharedPreferences

    @Volatile
    private var cache: MutableSet<String> = mutableSetOf()

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        cache = prefs.getStringSet(KEY_FAVORITES, emptySet())?.toMutableSet() ?: mutableSetOf()
        migrateLegacyFormatIfNeeded()
    }

    fun isFavorite(documentId: String): Boolean = documentId in cache

    /** @return true if the item is now favourited. */
    fun toggle(documentId: String): Boolean {
        val nowFavorite = if (documentId in cache) {
            cache.remove(documentId)
            false
        } else {
            cache.add(documentId)
            true
        }
        persist()
        return nowFavorite
    }

    /** Snapshot for the playlist builder. */
    fun snapshot(): Set<String> = cache.toSet()

    /**
     * Drops favourites whose files no longer exist, so the set does not grow forever as
     * photos are swapped in and out of the folder over the years.
     */
    fun retainOnly(existingDocumentIds: Set<String>) {
        if (cache.isEmpty()) return
        val before = cache.size
        cache.retainAll(existingDocumentIds)
        if (cache.size != before) persist()
    }

    private fun persist() {
        // commit(), not apply(): a photo frame loses power abruptly, and an async write
        // that has not reached disk is a favourite the user has to set again.
        prefs.edit().putStringSet(KEY_FAVORITES, cache.toSet()).commit()
    }

    private fun migrateLegacyFormatIfNeeded() {
        val legacy = prefs.getString(LEGACY_KEY, null) ?: return

        // The old format stored full URIs. The document ID is the segment after
        // "/document/", which is the best identity recoverable without a folder rescan.
        legacy.split(LEGACY_DELIMITER)
            .asSequence()
            .filter { it.isNotBlank() }
            .mapNotNull { uri -> uri.substringAfterLast("/document/", "").takeIf(String::isNotEmpty) }
            .map { Uri.decode(it) }
            .forEach { cache.add(it) }

        prefs.edit()
            .putStringSet(KEY_FAVORITES, cache.toSet())
            .remove(LEGACY_KEY)
            .commit()
    }
}
