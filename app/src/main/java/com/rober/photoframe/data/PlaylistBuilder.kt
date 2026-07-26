package com.rober.photoframe.data

import com.rober.photoframe.model.MediaItem
import kotlin.random.Random

/**
 * Turns the scanned media library into the ordered list the slideshow actually walks.
 *
 * Kept as pure functions with no Android dependencies so the ordering rules — which are easy
 * to get subtly wrong — can be unit tested without a device.
 */
object PlaylistBuilder {
    /** How many times a favourited photo appears in a shuffled playlist. */
    const val FAVORITE_WEIGHT = 3

    /**
     * Builds the playlist.
     *
     * When [shuffle] is off the library is sorted by name and **favourite weighting is not
     * applied**. This is deliberate: duplicating an entry inside a sorted list would show the
     * same photo three times in a row, which is not what "show my favourites more often"
     * means. Weighting only makes sense against a randomised order.
     *
     * @param favoriteIds document IDs of favourited items
     * @param random injectable for deterministic tests
     */
    fun build(
        library: List<MediaItem>,
        favoriteIds: Set<String>,
        shuffle: Boolean,
        random: Random = Random.Default,
    ): List<MediaItem> {
        if (library.isEmpty()) return emptyList()

        if (!shuffle) {
            return library.sortedWith(NATURAL_NAME_ORDER)
        }

        val weights =
            library.associate { item ->
                item.documentId to if (item.documentId in favoriteIds) FAVORITE_WEIGHT else 1
            }

        return interleave(library.shuffled(random), weights)
    }

    /**
     * Expands each item to its weight and spaces the copies out.
     *
     * A naive "duplicate then shuffle" sometimes places two copies of the same photo back to
     * back, which reads to the user as the slideshow being stuck. This uses the standard
     * reorganisation approach: take the items in descending weight order and deal them into
     * the even slots first, then the odd ones. That is provably free of adjacent duplicates
     * whenever it is possible at all — that is, when no single item accounts for more than
     * half the playlist.
     *
     * The only cases where it cannot succeed are tiny libraries (one or two photos, at least
     * one favourited), where no arrangement avoids repetition. Those degrade quietly rather
     * than looping.
     *
     * Randomness comes from the caller having already shuffled [items]; the sort below is
     * stable, so items of equal weight keep that random order.
     */
    internal fun interleave(
        items: List<MediaItem>,
        weights: Map<String, Int>,
    ): List<MediaItem> {
        val ordered = items.sortedByDescending { weights[it.documentId] ?: 1 }
        val total = ordered.sumOf { weights[it.documentId] ?: 1 }
        if (total == 0) return emptyList()

        val slots = arrayOfNulls<MediaItem>(total)
        var position = 0

        for (item in ordered) {
            repeat(weights[item.documentId] ?: 1) {
                slots[position] = item
                position += 2
                if (position >= total) position = 1
            }
        }

        @Suppress("UNCHECKED_CAST")
        return slots.toList() as List<MediaItem>
    }

    /**
     * Orders filenames the way a person expects: IMG_2 before IMG_10.
     *
     * A plain string sort puts IMG_10 first, which looks broken when photos are numbered
     * sequentially — the most common way holiday photos are named.
     */
    internal val NATURAL_NAME_ORDER: Comparator<MediaItem> =
        Comparator { a, b -> compareNatural(a.name, b.name) }

    internal fun compareNatural(
        left: String,
        right: String,
    ): Int {
        var i = 0
        var j = 0
        while (i < left.length && j < right.length) {
            val cl = left[i]
            val cr = right[j]

            if (cl.isDigit() && cr.isDigit()) {
                // Compare whole runs of digits numerically, ignoring leading zeros.
                var endI = i
                while (endI < left.length && left[endI].isDigit()) endI++
                var endJ = j
                while (endJ < right.length && right[endJ].isDigit()) endJ++

                val numL = left.substring(i, endI).trimStart('0')
                val numR = right.substring(j, endJ).trimStart('0')

                if (numL.length != numR.length) return numL.length - numR.length
                val cmp = numL.compareTo(numR)
                if (cmp != 0) return cmp

                i = endI
                j = endJ
            } else {
                val cmp = cl.lowercaseChar().compareTo(cr.lowercaseChar())
                if (cmp != 0) return cmp
                i++
                j++
            }
        }
        return (left.length - i) - (right.length - j)
    }
}
