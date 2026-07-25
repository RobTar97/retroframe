package com.rober.photoframe.data

import com.rober.photoframe.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaTypesTest {

    @Test
    fun `mime type identifies images`() {
        assertEquals(MediaType.IMAGE, MediaTypes.classify("image/jpeg", "holiday.jpg"))
        assertEquals(MediaType.IMAGE, MediaTypes.classify("image/png", "screenshot.png"))
        assertEquals(MediaType.IMAGE, MediaTypes.classify("image/heic", "IMG_0001.heic"))
    }

    @Test
    fun `mime type identifies videos`() {
        assertEquals(MediaType.VIDEO, MediaTypes.classify("video/mp4", "clip.mp4"))
        assertEquals(MediaType.VIDEO, MediaTypes.classify("video/x-matroska", "clip.mkv"))
    }

    @Test
    fun `mime type is case insensitive`() {
        assertEquals(MediaType.IMAGE, MediaTypes.classify("IMAGE/JPEG", "a.jpg"))
    }

    @Test
    fun `falls back to extension when provider reports a generic mime type`() {
        // Several older document providers report octet-stream for everything, which is why
        // the extension is consulted rather than trusted blindly first.
        assertEquals(
            MediaType.IMAGE,
            MediaTypes.classify("application/octet-stream", "photo.JPG"),
        )
        assertEquals(
            MediaType.VIDEO,
            MediaTypes.classify("application/octet-stream", "movie.MOV"),
        )
    }

    @Test
    fun `falls back to extension when mime type is missing`() {
        assertEquals(MediaType.IMAGE, MediaTypes.classify(null, "photo.webp"))
        assertEquals(MediaType.VIDEO, MediaTypes.classify(null, "video.3gp"))
    }

    @Test
    fun `skips files that are not displayable`() {
        assertNull(MediaTypes.classify("application/pdf", "manual.pdf"))
        assertNull(MediaTypes.classify(null, "notes.txt"))
        assertNull(MediaTypes.classify(null, "README"))
        assertNull(MediaTypes.classify(null, null))
    }

    @Test
    fun `handles filenames with dots and no extension`() {
        assertEquals(MediaType.IMAGE, MediaTypes.classify(null, "my.holiday.photo.jpg"))
        assertNull(MediaTypes.classify(null, "archive."))
    }
}
