package com.rober.photoframe

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Guards two things the compiler cannot see.
 *
 * **The privacy claim.** The README, the website and the store listing all say RetroFrame
 * cannot reach a network. That is only true as long as no dependency quietly merges a network
 * permission into the manifest — which has already happened once, when Media3 injected
 * ACCESS_NETWORK_STATE for adaptive streaming the app never uses. A promise in a readme is
 * not a guarantee; this test is.
 *
 * **The receivers.** They are registered by class name in the manifest and kept by name in
 * the ProGuard rules, so nothing in the build fails if a package move or an R8 rename breaks
 * the link. The app would simply stop waking up, stop sounding its alarm and stop restarting
 * after a power cut — silently, on someone's shelf.
 */
@RunWith(AndroidJUnit4::class)
class ManifestInstrumentedTest {
    private lateinit var context: Context
    private lateinit var pm: PackageManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        pm = context.packageManager
    }

    private fun packageName() = context.packageName.removeSuffix(".debug")

    private fun declaredPermissions(): List<String> =
        pm.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions
            ?.filter { it.startsWith("android.permission.") }
            .orEmpty()

    @Test
    fun appCannotReachTheNetwork() {
        val declared = declaredPermissions()

        assertTrue(
            "INTERNET is declared. RetroFrame's whole claim is that it physically cannot " +
                "phone home; if a dependency needs this, the claim must change too. " +
                "Declared: $declared",
            "android.permission.INTERNET" !in declared,
        )
        assertTrue(
            "ACCESS_NETWORK_STATE is back. Media3 injects it for adaptive streaming; it " +
                "is removed with tools:node=\"remove\" in AndroidManifest.xml. " +
                "Declared: $declared",
            "android.permission.ACCESS_NETWORK_STATE" !in declared,
        )
    }

    @Test
    fun appAsksForNoStoragePermissions() {
        val declared = declaredPermissions()
        val storage = declared.filter { "STORAGE" in it || "MEDIA" in it }

        assertTrue(
            "Media is read through the Storage Access Framework, which needs no permission. " +
                "Found: $storage",
            storage.isEmpty(),
        )
    }

    @Test
    fun permissionListIsExactlyWhatIsDocumented() {
        // Boot, wake lock, exact alarm, notifications — and nothing else. Anything new here
        // is a deliberate decision that should also update README.md and the site.
        assertEquals(
            setOf(
                "android.permission.RECEIVE_BOOT_COMPLETED",
                "android.permission.WAKE_LOCK",
                "android.permission.SCHEDULE_EXACT_ALARM",
                "android.permission.POST_NOTIFICATIONS",
            ),
            declaredPermissions().toSet(),
        )
    }

    @Test
    fun exactAlarmPermissionIsSpelledCorrectly() {
        // SCHEDULE_EXACT_ALARMS (plural) is not a real permission. It was declared that way
        // for a long time, so the system never offered it and the alarm silently ran inexact.
        val declared = declaredPermissions()
        assertTrue(
            "SCHEDULE_EXACT_ALARM (singular) must be declared. Found: $declared",
            "android.permission.SCHEDULE_EXACT_ALARM" in declared,
        )
    }

    @Test
    fun everyScheduledReceiverIsStillReachable() {
        val receivers = listOf(
            "${packageName()}.boot.BootReceiver",
            "${packageName()}.schedule.AlarmReceiver",
            "${packageName()}.schedule.AlarmDismissReceiver",
            "${packageName()}.schedule.DailySchedule\$ScheduleReceiver",
        )

        for (name in receivers) {
            val info = pm.getReceiverInfo(
                android.content.ComponentName(context.packageName, name),
                0,
            )
            assertTrue("$name is registered but disabled", info.enabled)
        }
    }

    @Test
    fun scheduleBroadcastsResolveToAReceiver() {
        // A registered class is not enough; the intent-filters have to match what the
        // schedulers actually broadcast.
        for (action in BROADCAST_ACTIONS) {
            val intent = Intent(action).setPackage(context.packageName)
            assertTrue(
                "Nothing handles $action — an intent-filter and its sender have drifted apart",
                pm.queryBroadcastReceivers(intent, 0).isNotEmpty(),
            )
        }
    }

    private companion object {
        val BROADCAST_ACTIONS = listOf(
            "com.rober.photoframe.ACTION_WAKE_UP",
            "com.rober.photoframe.ACTION_SLEEP",
            "com.rober.photoframe.ACTION_ALARM",
        )
    }
}
