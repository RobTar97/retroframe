package com.rober.photoframe.data

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Exercises the recursive folder walk against a fake storage provider.
 *
 * The parts worth testing here are the ones that never run in normal use and cannot be
 * reproduced on demand on a device: the depth limit, the item cap, and the cycle guard. A
 * provider that reports a folder as its own descendant is not hypothetical — symlinked media
 * directories on old vendor ROMs do exactly that — and the consequence of getting it wrong is
 * an unkillable loop on a tablet nobody is watching.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class PhotoRepositoryScanTest {
    private lateinit var repository: PhotoRepository

    @Before
    fun setUp() {
        FakeDocumentsProvider.folders.clear()
        org.robolectric.Robolectric.setupContentProvider(
            FakeDocumentsProvider::class.java,
            AUTHORITY,
        )
        repository = PhotoRepository(RuntimeEnvironment.getApplication())
    }

    private fun treeUri(rootId: String): Uri =
        DocumentsContract.buildTreeDocumentUri(AUTHORITY, rootId)

    // ------------------------------------------------------------------ basics

    @Test
    fun `reads the chosen folder`() =
        runTest {
            FakeDocumentsProvider.folders["root"] =
                listOf(photo("a.jpg"), photo("b.png"), Row("notes.txt", "text/plain"))

            val items = repository.scan(treeUri("root"), includeVideos = true)

            assertEquals(listOf("a.jpg", "b.png"), items.map { it.name })
        }

    @Test
    fun `ignores subfolders when told to`() =
        runTest {
            FakeDocumentsProvider.folders["root"] = listOf(photo("top.jpg"), dir("child"))
            FakeDocumentsProvider.folders["child"] = listOf(photo("hidden.jpg"))

            val items =
                repository.scan(treeUri("root"), includeVideos = true, includeSubfolders = false)

            assertEquals(listOf("top.jpg"), items.map { it.name })
        }

    @Test
    fun `descends into subfolders when asked`() =
        runTest {
            FakeDocumentsProvider.folders["root"] = listOf(photo("top.jpg"), dir("2019"))
            FakeDocumentsProvider.folders["2019"] = listOf(photo("italy.jpg"), dir("rome"))
            FakeDocumentsProvider.folders["rome"] = listOf(photo("colosseum.jpg"))

            val items =
                repository.scan(treeUri("root"), includeVideos = true, includeSubfolders = true)

            assertEquals(
                setOf("top.jpg", "italy.jpg", "colosseum.jpg"),
                items.map { it.name }.toSet(),
            )
        }

    @Test
    fun `videos are still filtered out inside subfolders`() =
        runTest {
            FakeDocumentsProvider.folders["root"] = listOf(dir("clips"))
            FakeDocumentsProvider.folders["clips"] = listOf(photo("a.jpg"), video("b.mp4"))

            val items =
                repository.scan(treeUri("root"), includeVideos = false, includeSubfolders = true)

            assertEquals(listOf("a.jpg"), items.map { it.name })
        }

    // ------------------------------------------------------------------ guards

    /**
     * These two assert on the *list*, not the set, and that is the whole point.
     *
     * A cycle does not hang the walk — MAX_DEPTH guarantees it ends either way. What it does
     * without the visited set is re-walk the loop at every remaining level, so the same photo
     * comes back four or five times and the slideshow shows it four or five times as often.
     * Comparing sets hides exactly that, and an earlier version of this test did.
     */
    @Test
    fun `a folder that contains itself is not walked twice`() =
        runTest {
            FakeDocumentsProvider.folders["root"] = listOf(photo("a.jpg"), dir("loop"))
            FakeDocumentsProvider.folders["loop"] = listOf(photo("b.jpg"), dir("root"))

            val items =
                repository.scan(treeUri("root"), includeVideos = true, includeSubfolders = true)

            assertEquals(listOf("a.jpg", "b.jpg"), items.map { it.name })
        }

    @Test
    fun `two folders pointing at each other are each walked once`() =
        runTest {
            FakeDocumentsProvider.folders["root"] = listOf(dir("a"))
            FakeDocumentsProvider.folders["a"] = listOf(photo("a.jpg"), dir("b"))
            FakeDocumentsProvider.folders["b"] = listOf(photo("b.jpg"), dir("a"))

            val items =
                repository.scan(treeUri("root"), includeVideos = true, includeSubfolders = true)

            assertEquals(listOf("a.jpg", "b.jpg"), items.map { it.name })
        }

    @Test
    fun `stops descending past the depth limit`() =
        runTest {
            // root -> d1 -> d2 -> ... -> d8, each holding one photo named for its depth.
            FakeDocumentsProvider.folders["root"] = listOf(photo("d0.jpg"), dir("d1"))
            for (depth in 1..8) {
                FakeDocumentsProvider.folders["d$depth"] =
                    listOf(photo("d$depth.jpg"), dir("d${depth + 1}"))
            }

            val items =
                repository.scan(treeUri("root"), includeVideos = true, includeSubfolders = true)
            val names = items.map { it.name }.toSet()

            assertTrue("The limit is meant to be five levels below the root", "d5.jpg" in names)
            assertTrue("Anything deeper than five levels must be left alone", "d6.jpg" !in names)
        }

    @Test
    fun `an unreadable folder yields nothing rather than throwing`() =
        runTest {
            // No entry in the fake provider at all — the SD card was pulled out.
            val items = repository.scan(treeUri("missing"), includeVideos = true)

            assertEquals(emptyList<String>(), items.map { it.name })
        }

    // --------------------------------------------------------------- signature

    @Test
    fun `the signature notices a photo added to a subfolder`() =
        runTest {
            FakeDocumentsProvider.folders["root"] = listOf(dir("child"))
            FakeDocumentsProvider.folders["child"] = listOf(photo("a.jpg"))

            val before = repository.signature(treeUri("root"), includeSubfolders = true)

            FakeDocumentsProvider.folders["child"] = listOf(photo("a.jpg"), photo("b.jpg"))
            val after = repository.signature(treeUri("root"), includeSubfolders = true)

            // If these compare equal, the safety-net poll is blind to subfolder changes and
            // photos added to "Photos/2020" would never appear until the app was restarted.
            assertTrue("Signature did not change: $before == $after", before != after)
        }

    @Test
    fun `an unreadable folder has an unknown signature`() =
        runTest {
            assertEquals(
                FolderSignature.UNKNOWN,
                repository.signature(treeUri("missing"), includeSubfolders = true),
            )
        }

    // ------------------------------------------------------------------ fakes

    private fun photo(name: String) = Row(name, "image/jpeg")

    private fun video(name: String) = Row(name, "video/mp4")

    private fun dir(id: String) = Row(id, DocumentsContract.Document.MIME_TYPE_DIR)

    data class Row(val id: String, val mimeType: String)

    /**
     * The smallest thing that behaves like a document provider: a map from parent document ID
     * to its children. It answers only the children query the repository makes.
     */
    class FakeDocumentsProvider : ContentProvider() {
        override fun onCreate() = true

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): Cursor? {
            val parentId = DocumentsContract.getDocumentId(uri)
            val children = folders[parentId] ?: return null

            val columns =
                projection ?: arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                )
            val cursor = MatrixCursor(columns)

            for (child in children) {
                cursor.addRow(
                    columns.map { column ->
                        when (column) {
                            DocumentsContract.Document.COLUMN_DOCUMENT_ID -> child.id
                            DocumentsContract.Document.COLUMN_DISPLAY_NAME -> child.id
                            DocumentsContract.Document.COLUMN_MIME_TYPE -> child.mimeType
                            DocumentsContract.Document.COLUMN_LAST_MODIFIED -> 1_000L
                            DocumentsContract.Document.COLUMN_SIZE -> 2_048L
                            else -> null
                        }
                    },
                )
            }
            return cursor
        }

        override fun getType(uri: Uri): String? = null

        override fun insert(
            uri: Uri,
            values: ContentValues?,
        ): Uri? = null

        override fun delete(
            uri: Uri,
            selection: String?,
            selectionArgs: Array<out String>?,
        ): Int = 0

        override fun update(
            uri: Uri,
            values: ContentValues?,
            selection: String?,
            selectionArgs: Array<out String>?,
        ): Int = 0

        companion object {
            val folders: MutableMap<String, List<Row>> = mutableMapOf()
        }
    }

    private companion object {
        const val AUTHORITY = "com.rober.photoframe.test.documents"
    }
}
