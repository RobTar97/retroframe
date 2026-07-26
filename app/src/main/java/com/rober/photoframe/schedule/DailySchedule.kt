package com.rober.photoframe.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.util.Log
import com.rober.photoframe.MainActivity
import com.rober.photoframe.settings.PhotoframePreferences
import java.util.Calendar

/**
 * The daily wake and sleep schedule.
 *
 * ## Exactness
 *
 * These alarms deliberately do **not** demand exact scheduling. A photo frame waking at
 * 07:02 instead of 07:00 is indistinguishable to the user, and `setWindow` lets the system
 * batch the wake-up with other work rather than forcing a dedicated CPU wake — the cheaper
 * choice on a device that may be running on a tired battery.
 *
 * The previous implementation called `canScheduleExactAlarms()` and, if the permission was
 * missing, returned silently: the schedule the user had configured simply never fired, and
 * nothing said so. Now the schedule always works, and exactness is a bonus when granted.
 */
object DailySchedule {
    private const val TAG = "DailySchedule"

    const val ACTION_WAKE_UP = "com.rober.photoframe.ACTION_WAKE_UP"
    const val ACTION_SLEEP = "com.rober.photoframe.ACTION_SLEEP"

    private const val WAKELOCK_TAG = "RetroFrame:WakeUp"

    /** How long the screen is forced on at wake time before normal rules resume. */
    private const val WAKE_HOLD_MS = 10_000L

    /** Tolerance for inexact scheduling. */
    private const val WINDOW_MS = 60_000L

    fun scheduleAlarms(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        cancelAlarms(context)

        PhotoframePreferences.wakeTimeMinutes
            .takeIf { it != PhotoframePreferences.TIME_DISABLED }
            ?.let { schedule(context, alarmManager, it, ACTION_WAKE_UP) }

        PhotoframePreferences.sleepTimeMinutes
            .takeIf { it != PhotoframePreferences.TIME_DISABLED }
            ?.let { schedule(context, alarmManager, it, ACTION_SLEEP) }
    }

    private fun schedule(
        context: Context,
        alarmManager: AlarmManager,
        minutesSinceMidnight: Int,
        action: String,
    ) {
        val triggerAt = nextOccurrence(minutesSinceMidnight)

        AlarmCompat.schedule(
            alarmManager = alarmManager,
            triggerAtMillis = triggerAt,
            pendingIntent = pendingIntentFor(context, action),
            windowMillis = WINDOW_MS,
            // Still fires, just not to the second. Better than not firing at all.
            allowInexact = true,
        )
        Log.d(TAG, "Scheduled $action for ${java.util.Date(triggerAt)}")
    }

    /** True when the system will honour exact alarms without user intervention. */
    fun canScheduleExact(alarmManager: AlarmManager): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    fun canScheduleExact(context: Context): Boolean =
        canScheduleExact(context.getSystemService(Context.ALARM_SERVICE) as AlarmManager)

    private fun nextOccurrence(minutesSinceMidnight: Int): Long {
        val calendar =
            Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, minutesSinceMidnight / 60)
                set(Calendar.MINUTE, minutesSinceMidnight % 60)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
        if (calendar.timeInMillis <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
        return calendar.timeInMillis
    }

    private fun pendingIntentFor(
        context: Context,
        action: String,
    ): PendingIntent {
        val intent = Intent(context, ScheduleReceiver::class.java).setAction(action)
        return PendingIntent.getBroadcast(
            context,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun cancelAlarms(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntentFor(context, ACTION_WAKE_UP))
        alarmManager.cancel(pendingIntentFor(context, ACTION_SLEEP))
    }

    class ScheduleReceiver : BroadcastReceiver() {
        override fun onReceive(
            context: Context,
            intent: Intent,
        ) {
            PhotoframePreferences.init(context)
            Log.d(TAG, "Alarm fired: ${intent.action}")

            when (intent.action) {
                ACTION_WAKE_UP -> wakeUp(context)
                ACTION_SLEEP -> launchMode(context, MainActivity.MODE_CLOCK)
            }

            // Re-arm for tomorrow. Android does not repeat these for us.
            scheduleAlarms(context)
        }

        private fun wakeUp(context: Context) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager

            @Suppress("DEPRECATION") // No non-deprecated way to force the screen on.
            val wakeLock =
                powerManager.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                        PowerManager.ACQUIRE_CAUSES_WAKEUP or
                        PowerManager.ON_AFTER_RELEASE,
                    WAKELOCK_TAG,
                )

            // acquire(timeout) returns immediately and releases itself when the timeout
            // expires. The previous code wrapped this in try/finally and released the lock
            // microseconds later, so the intended hold never actually happened — the screen
            // stayed on only by luck, via ON_AFTER_RELEASE resetting the user-activity timer.
            // Let the timeout do its job instead.
            wakeLock.acquire(WAKE_HOLD_MS)

            launchMode(context, MainActivity.MODE_PHOTO)
        }

        private fun launchMode(
            context: Context,
            mode: String,
        ) {
            val intent =
                Intent(context, MainActivity::class.java)
                    .addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP or
                            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT,
                    )
                    .putExtra(MainActivity.EXTRA_MODE, mode)

            try {
                context.startActivity(intent)
            } catch (e: Exception) {
                // Background activity launches are restricted on newer Android versions.
                Log.e(TAG, "Could not bring RetroFrame to the front", e)
            }
        }
    }
}
