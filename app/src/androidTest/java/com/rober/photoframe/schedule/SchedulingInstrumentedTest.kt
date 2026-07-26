package com.rober.photoframe.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rober.photoframe.data.Alarm
import com.rober.photoframe.data.AlarmSettings
import com.rober.photoframe.settings.PhotoframePreferences
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * These exist because of one specific bug.
 *
 * `setExactAndAllowWhileIdle` arrived in API 23. This app's minimum is API 22. For most of the
 * project's life every scheduled alarm called it unconditionally, which means the wake
 * schedule and the morning alarm would have thrown `NoSuchMethodError` on Android 5.1 — the
 * oldest version the app claims to support, and the version nobody has hardware for.
 *
 * Nothing caught it. Unit tests do not execute framework code, and the build is perfectly
 * happy to compile a call that will not exist at runtime. It was found by reading a lint
 * warning.
 *
 * Every test below simply *calls the scheduling paths for real* on whatever API level the
 * emulator is running. Run against API 22 in CI, they turn that entire class of bug from
 * "discovered by a user whose photo frame never woke up" into a red build.
 */
@RunWith(AndroidJUnit4::class)
class SchedulingInstrumentedTest {
    private lateinit var context: Context
    private lateinit var alarmManager: AlarmManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        PhotoframePreferences.init(context)
        AlarmSettings.init(context)
    }

    private fun pendingIntent(requestCode: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, AlarmReceiver::class.java).setAction(AlarmReceiver.ACTION_ALARM),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /** The regression test. On API 22 this fails outright if AlarmCompat picks the wrong API. */
    @Test
    fun alarmCompat_schedulesOnThisApiLevelWithoutThrowing() {
        AlarmCompat.schedule(
            alarmManager = alarmManager,
            triggerAtMillis = System.currentTimeMillis() + 60_000,
            pendingIntent = pendingIntent(9001),
            windowMillis = 60_000,
            allowInexact = true,
        )
    }

    /** The path taken when the system withholds exact alarms. Must also survive API 22. */
    @Test
    fun alarmCompat_inexactFallbackDoesNotThrow() {
        AlarmCompat.schedule(
            alarmManager = alarmManager,
            triggerAtMillis = System.currentTimeMillis() + 120_000,
            pendingIntent = pendingIntent(9002),
            windowMillis = 60_000,
            allowInexact = false,
        )
    }

    /** Exercises the real wake and sleep scheduling, including its PendingIntent plumbing. */
    @Test
    fun dailySchedule_armsWakeAndSleepWithoutThrowing() {
        PhotoframePreferences.wakeTimeMinutes = 7 * 60
        PhotoframePreferences.sleepTimeMinutes = 23 * 60

        DailySchedule.scheduleAlarms(context)

        // Disabling both must also be safe — it takes the cancel-only path.
        PhotoframePreferences.wakeTimeMinutes = PhotoframePreferences.TIME_DISABLED
        PhotoframePreferences.sleepTimeMinutes = PhotoframePreferences.TIME_DISABLED
        DailySchedule.scheduleAlarms(context)
    }

    /** The morning alarm asks for exact scheduling, so it takes a different branch. */
    @Test
    fun alarmScheduler_scheduleAndCancelDoNotThrow() {
        val alarm = Alarm(hour = 7, minute = 30, enabled = true)
        AlarmScheduler.schedule(context, alarm)
        AlarmScheduler.cancel(context, alarm)
    }

    /** Whatever this returns, asking must not blow up on old API levels. */
    @Test
    fun canScheduleExact_isAnswerableOnEveryApiLevel() {
        DailySchedule.canScheduleExact(context)
        DailySchedule.canScheduleExact(alarmManager)
    }
}
