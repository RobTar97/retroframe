package com.rober.photoframe.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The night-dimming conversion, tested because the failure mode is nasty and silent.
 *
 * Handing a window a `screenBrightness` of 0 blacks the panel out completely on a good many
 * devices. The user would be looking at a tablet that appears dead, with no way to find the
 * controls to undo it — because the controls are on the screen that just went black.
 */
class BrightnessTest {
    @Test
    fun `the disabled sentinel hands control back to the system`() {
        assertEquals(
            Brightness.OVERRIDE_NONE,
            Brightness.toWindowValue(PhotoframePreferences.BRIGHTNESS_SYSTEM),
            0f,
        )
    }

    @Test
    fun `values below the floor mean off, not almost off`() {
        // 0-4% must not be treated as "very dim"; it is the slider sitting at its left end.
        for (percent in 0 until PhotoframePreferences.MIN_NIGHT_BRIGHTNESS) {
            assertEquals(
                "$percent% should disable the override, not dim to near-black",
                Brightness.OVERRIDE_NONE,
                Brightness.toWindowValue(percent),
                0f,
            )
        }
    }

    @Test
    fun `no enabled value can black the screen out`() {
        for (percent in PhotoframePreferences.MIN_NIGHT_BRIGHTNESS..100) {
            val value = Brightness.toWindowValue(percent)
            assertTrue(
                "$percent% produced $value, which is at or below the blackout threshold",
                value >= PhotoframePreferences.MIN_NIGHT_BRIGHTNESS / 100f,
            )
        }
    }

    @Test
    fun `percentages map onto the window's 0 to 1 range`() {
        assertEquals(0.05f, Brightness.toWindowValue(5), 0.001f)
        assertEquals(0.5f, Brightness.toWindowValue(50), 0.001f)
        assertEquals(1f, Brightness.toWindowValue(100), 0.001f)
    }

    @Test
    fun `out of range values are clamped rather than passed through`() {
        // A corrupt or hand-edited preferences file must not reach the window API.
        assertEquals(1f, Brightness.toWindowValue(500), 0.001f)
        assertEquals(Brightness.OVERRIDE_NONE, Brightness.toWindowValue(-99), 0f)
    }
}
