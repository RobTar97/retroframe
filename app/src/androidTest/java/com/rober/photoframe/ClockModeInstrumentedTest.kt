package com.rober.photoframe

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.rober.photoframe.ui.ClockFragment
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Clock mode is what the sleep schedule switches to every night, so it has to survive being
 * launched cold from a broadcast receiver on every supported Android version.
 *
 * Only clock mode is covered here. Photo mode opens the system folder picker when no folder
 * has been granted, and a SAF grant cannot be scripted — the user confirming it in the
 * picker *is* the security model. Driving that from a test would mean either automating
 * another app's UI or adding a test-only backdoor to production code; neither is worth it.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class ClockModeInstrumentedTest {
    private fun clockIntent(): Intent =
        Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_MODE, MainActivity.MODE_CLOCK)

    @Test
    fun launchingInClockModeShowsTheClock() {
        ActivityScenario.launch<MainActivity>(clockIntent()).use { scenario ->
            scenario.onActivity { activity ->
                val fragment = activity.supportFragmentManager.findFragmentById(R.id.container)
                assertTrue(
                    "Expected ClockFragment, found ${fragment?.javaClass?.simpleName}",
                    fragment is ClockFragment,
                )
            }
        }
    }

    @Test
    fun clockModeLetsTheScreenTurnOff() {
        // The entire point of sleep mode: the wake lock flag must be clear, or the tablet
        // stays lit all night and cooks its own battery.
        ActivityScenario.launch<MainActivity>(clockIntent()).use { scenario ->
            scenario.onActivity { activity ->
                val flags = activity.window.attributes.flags
                val keepOn = android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                assertTrue(
                    "FLAG_KEEP_SCREEN_ON is still set in clock mode",
                    flags and keepOn == 0,
                )
            }
        }
    }

    @Test
    fun switchingModesTwiceDoesNotRebuildTheSameFragment() {
        ActivityScenario.launch<MainActivity>(clockIntent()).use { scenario ->
            scenario.onActivity { activity ->
                val first = activity.supportFragmentManager.findFragmentById(R.id.container)
                activity.switchToClockMode()
                val second = activity.supportFragmentManager.findFragmentById(R.id.container)
                assertTrue(
                    "Re-entering clock mode replaced the fragment. A re-fired alarm would " +
                        "then tear down and rebuild a running screen every time.",
                    first === second,
                )
            }
        }
    }
}
