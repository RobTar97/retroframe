package com.rober.photoframe.settings

import com.rober.photoframe.settings.PhotoframePreferences.TIME_DISABLED
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimeOfDayTest {
    @Test
    fun `parses valid times`() {
        assertEquals(0, TimeOfDay.parse("00:00"))
        assertEquals(7 * 60, TimeOfDay.parse("07:00"))
        assertEquals(23 * 60 + 59, TimeOfDay.parse("23:59"))
        assertEquals(13 * 60 + 5, TimeOfDay.parse("13:05"))
    }

    @Test
    fun `parses times without leading zeros`() {
        assertEquals(7 * 60 + 5, TimeOfDay.parse("7:5"))
        assertEquals(9 * 60, TimeOfDay.parse("9:00"))
    }

    @Test
    fun `tolerates surrounding whitespace`() {
        assertEquals(7 * 60, TimeOfDay.parse("  07:00  "))
        assertEquals(7 * 60, TimeOfDay.parse("07 : 00"))
    }

    @Test
    fun `empty input disables the schedule`() {
        assertEquals(TIME_DISABLED, TimeOfDay.parse(null))
        assertEquals(TIME_DISABLED, TimeOfDay.parse(""))
        assertEquals(TIME_DISABLED, TimeOfDay.parse("   "))
    }

    @Test
    fun `rejects out of range values`() {
        // The old implementation accepted these and stored a nonsense minute count, which
        // produced an alarm that either never fired or fired at an unpredictable time.
        assertEquals(TIME_DISABLED, TimeOfDay.parse("24:00"))
        assertEquals(TIME_DISABLED, TimeOfDay.parse("25:30"))
        assertEquals(TIME_DISABLED, TimeOfDay.parse("12:60"))
        assertEquals(TIME_DISABLED, TimeOfDay.parse("-1:00"))
    }

    @Test
    fun `rejects malformed input`() {
        assertEquals(TIME_DISABLED, TimeOfDay.parse("noon"))
        assertEquals(TIME_DISABLED, TimeOfDay.parse("7"))
        assertEquals(TIME_DISABLED, TimeOfDay.parse("7:00:00"))
        assertEquals(TIME_DISABLED, TimeOfDay.parse("::"))
        assertEquals(TIME_DISABLED, TimeOfDay.parse("7:oo"))
    }

    @Test
    fun `formats times with padding`() {
        assertEquals("00:00", TimeOfDay.format(0))
        assertEquals("07:00", TimeOfDay.format(7 * 60))
        assertEquals("23:59", TimeOfDay.format(23 * 60 + 59))
        assertEquals("09:05", TimeOfDay.format(9 * 60 + 5))
    }

    @Test
    fun `formats disabled and invalid values as null`() {
        assertNull(TimeOfDay.format(TIME_DISABLED))
        assertNull(TimeOfDay.format(-100))
        assertNull(TimeOfDay.format(24 * 60))
    }

    @Test
    fun `parse and format round trip`() {
        for (minutes in 0 until 24 * 60) {
            assertEquals(minutes, TimeOfDay.parse(TimeOfDay.format(minutes)))
        }
    }
}
