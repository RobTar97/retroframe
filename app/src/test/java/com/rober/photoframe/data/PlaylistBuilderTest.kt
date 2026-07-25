package com.rober.photoframe.data

import android.net.Uri
import com.rober.photoframe.model.MediaItem
import com.rober.photoframe.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.random.Random

/**
 * Robolectric is used only because [MediaItem] holds an android.net.Uri. The logic under
 * test is pure and SDK-independent.
 *
 * The SDK is pinned to 34 because Robolectric only ships shadows up to that level, while the
 * app targets 36. Leaving it unpinned makes the whole class fail to initialise with
 * "targetSdkVersion=36 > maxSdkVersion=34".
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class PlaylistBuilderTest {

    private fun item(name: String, id: String = name) = MediaItem(
        documentId = id,
        uri = Uri.parse("content://test/$id"),
        name = name,
        type = MediaType.IMAGE,
        dateModified = 0L,
        size = 0L,
    )

    // ------------------------------------------------------------- sorted mode

    @Test
    fun `shuffle off sorts by name`() {
        val library = listOf(item("charlie.jpg"), item("alpha.jpg"), item("bravo.jpg"))

        val playlist = PlaylistBuilder.build(library, emptySet(), shuffle = false)

        assertEquals(
            listOf("alpha.jpg", "bravo.jpg", "charlie.jpg"),
            playlist.map { it.name },
        )
    }

    @Test
    fun `sorted mode orders numbered photos naturally`() {
        // A plain string sort puts IMG_10 before IMG_2, which looks broken for the most
        // common way holiday photos are named.
        val library = listOf(
            item("IMG_10.jpg"), item("IMG_2.jpg"), item("IMG_1.jpg"), item("IMG_20.jpg"),
        )

        val playlist = PlaylistBuilder.build(library, emptySet(), shuffle = false)

        assertEquals(
            listOf("IMG_1.jpg", "IMG_2.jpg", "IMG_10.jpg", "IMG_20.jpg"),
            playlist.map { it.name },
        )
    }

    @Test
    fun `sorted mode ignores leading zeros when comparing numbers`() {
        val library = listOf(item("p007.jpg"), item("p10.jpg"), item("p0002.jpg"))

        val playlist = PlaylistBuilder.build(library, emptySet(), shuffle = false)

        assertEquals(listOf("p0002.jpg", "p007.jpg", "p10.jpg"), playlist.map { it.name })
    }

    @Test
    fun `sorted mode does not duplicate favourites`() {
        // Weighting a sorted list would show the same photo three times consecutively,
        // which is not what "show favourites more often" means.
        val library = listOf(item("a.jpg"), item("b.jpg"), item("c.jpg"))

        val playlist = PlaylistBuilder.build(library, setOf("a.jpg"), shuffle = false)

        assertEquals(3, playlist.size)
        assertEquals(1, playlist.count { it.documentId == "a.jpg" })
    }

    // ------------------------------------------------------------ shuffled mode

    @Test
    fun `shuffle on weights favourites`() {
        val library = listOf(item("a.jpg"), item("b.jpg"), item("c.jpg"))

        val playlist = PlaylistBuilder.build(
            library,
            favoriteIds = setOf("a.jpg"),
            shuffle = true,
            random = Random(1),
        )

        assertEquals(PlaylistBuilder.FAVORITE_WEIGHT, playlist.count { it.documentId == "a.jpg" })
        assertEquals(1, playlist.count { it.documentId == "b.jpg" })
        assertEquals(1, playlist.count { it.documentId == "c.jpg" })
        assertEquals(3 + 2, playlist.size)
    }

    @Test
    fun `shuffle on keeps every photo when nothing is favourited`() {
        val library = (1..20).map { item("photo$it.jpg") }

        val playlist = PlaylistBuilder.build(library, emptySet(), shuffle = true, random = Random(7))

        assertEquals(library.size, playlist.size)
        assertEquals(library.map { it.documentId }.toSet(), playlist.map { it.documentId }.toSet())
    }

    @Test
    fun `favourites never appear twice in a row`() {
        // A plain shuffle of a weighted list sometimes places duplicates adjacently, which
        // reads to the user as the slideshow being stuck.
        val library = (1..6).map { item("photo$it.jpg") }
        val favorites = setOf("photo1.jpg", "photo2.jpg", "photo3.jpg")

        repeat(200) { seed ->
            val playlist = PlaylistBuilder.build(
                library,
                favorites,
                shuffle = true,
                random = Random(seed),
            )
            val adjacent = playlist.zipWithNext().count { (a, b) -> a.documentId == b.documentId }
            assertTrue("seed $seed produced $adjacent adjacent duplicates", adjacent == 0)
        }
    }

    @Test
    fun `interleave preserves the multiset of items`() {
        val library = (1..5).map { item("photo$it.jpg") }
        val weights = library.associate { it.documentId to if (it.name == "photo1.jpg") 3 else 1 }

        val result = PlaylistBuilder.interleave(library, weights)

        assertEquals(weights.values.sum(), result.size)
        assertEquals(
            weights,
            result.groupingBy { it.documentId }.eachCount(),
        )
    }

    @Test
    fun `weighting holds across many favourite ratios`() {
        val library = (1..12).map { item("photo$it.jpg") }

        for (favouriteCount in 0..12) {
            val favorites = library.take(favouriteCount).map { it.documentId }.toSet()
            val playlist = PlaylistBuilder.build(library, favorites, shuffle = true, random = Random(favouriteCount))

            val expected = library.size + favouriteCount * (PlaylistBuilder.FAVORITE_WEIGHT - 1)
            assertEquals("with $favouriteCount favourites", expected, playlist.size)

            val adjacent = playlist.zipWithNext().count { (a, b) -> a.documentId == b.documentId }
            assertEquals("with $favouriteCount favourites", 0, adjacent)
        }
    }

    // ------------------------------------------------------------- edge cases

    @Test
    fun `empty library produces an empty playlist`() {
        assertTrue(PlaylistBuilder.build(emptyList(), emptySet(), shuffle = true).isEmpty())
        assertTrue(PlaylistBuilder.build(emptyList(), emptySet(), shuffle = false).isEmpty())
    }

    @Test
    fun `single favourited photo does not loop forever`() {
        val library = listOf(item("only.jpg"))

        val playlist = PlaylistBuilder.build(library, setOf("only.jpg"), shuffle = true)

        // Nothing to interleave with, so the duplicates simply remain. Documented here so
        // the degenerate case is a known outcome rather than a surprise.
        assertEquals(PlaylistBuilder.FAVORITE_WEIGHT, playlist.size)
    }

    @Test
    fun `tiny libraries where spacing is impossible still terminate`() {
        // With two photos and one favourited the playlist is 4 long and one photo accounts
        // for 3 of them, so some repetition is unavoidable. It must not hang or drop items.
        val library = listOf(item("a.jpg"), item("b.jpg"))

        val playlist = PlaylistBuilder.build(library, setOf("a.jpg"), shuffle = true, random = Random(3))

        assertEquals(4, playlist.size)
        assertEquals(3, playlist.count { it.documentId == "a.jpg" })
        assertEquals(1, playlist.count { it.documentId == "b.jpg" })
    }

    @Test
    fun `favourite ids that no longer exist are ignored`() {
        val library = listOf(item("a.jpg"), item("b.jpg"))

        val playlist = PlaylistBuilder.build(library, setOf("deleted.jpg"), shuffle = true)

        assertEquals(2, playlist.size)
    }

    @Test
    fun `natural comparison handles equal prefixes and mixed case`() {
        assertTrue(PlaylistBuilder.compareNatural("a1", "a2") < 0)
        assertTrue(PlaylistBuilder.compareNatural("a2", "a10") < 0)
        assertTrue(PlaylistBuilder.compareNatural("A1", "a1") == 0)
        assertTrue(PlaylistBuilder.compareNatural("photo", "photo1") < 0)
        assertTrue(PlaylistBuilder.compareNatural("b", "a") > 0)
    }
}
