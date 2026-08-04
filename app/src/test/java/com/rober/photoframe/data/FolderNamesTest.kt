package com.rober.photoframe.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Document IDs are opaque by contract, so this parser is best-effort and has to fail quietly.
 * Showing the wrong folder name would be worse than showing none.
 */
class FolderNamesTest {
    @Test
    fun `reads the folder name from internal storage`() {
        assertEquals("Pictures", FolderNames.fromTreeDocumentId("primary:Pictures"))
    }

    @Test
    fun `uses the last segment of a nested path`() {
        assertEquals("Holidays", FolderNames.fromTreeDocumentId("primary:Pictures/Holidays"))
        assertEquals("Rome", FolderNames.fromTreeDocumentId("primary:Photos/2019/Italy/Rome"))
    }

    @Test
    fun `handles removable storage, where the volume is a serial number`() {
        assertEquals("DCIM", FolderNames.fromTreeDocumentId("1A2B-3C4D:DCIM"))
    }

    @Test
    fun `a whole volume has no folder name`() {
        // The caller says "the whole storage volume" rather than inventing a name.
        assertNull(FolderNames.fromTreeDocumentId("primary:"))
        assertNull(FolderNames.fromTreeDocumentId("1A2B-3C4D:"))
    }

    @Test
    fun `trailing separators do not produce an empty name`() {
        assertEquals("Pictures", FolderNames.fromTreeDocumentId("primary:Pictures/"))
    }

    @Test
    fun `missing or blank input is not a name`() {
        assertNull(FolderNames.fromTreeDocumentId(null))
        assertNull(FolderNames.fromTreeDocumentId(""))
        assertNull(FolderNames.fromTreeDocumentId("   "))
    }

    @Test
    fun `an ID with no volume prefix yields nothing rather than a guess`() {
        // Some providers use opaque numeric IDs. "12" on screen would mean nothing to anyone.
        assertNull(FolderNames.fromTreeDocumentId("12"))
    }
}
