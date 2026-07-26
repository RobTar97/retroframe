package com.rober.photoframe.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.rober.photoframe.data.Alarm
import java.util.Calendar

/**
 * Schedules the morning alarm.
 *
 * Unlike the wake/sleep schedule, this one genuinely wants to be exact — an alarm clock that
 * fires "somewhere in the next minute" is not an alarm clock. When the permission is absent
 * it still schedules an inexact alarm rather than doing nothing, so the user is woken
 * approximately rather than not at all.
 */
object AlarmScheduler {
    private const val TAG = "AlarmScheduler"
    private const val WINDOW_MS = 60_000L

    fun schedule(
        context: Context,
        alarm: Alarm,
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAt = nextOccurrence(alarm)

        if (!DailySchedule.canScheduleExact(alarmManager)) {
            Log.w(TAG, "Exact alarms not permitted; falling back to an inexact window")
        }

        AlarmCompat.schedule(
            alarmManager = alarmManager,
            triggerAtMillis = triggerAt,
            pendingIntent = pendingIntentFor(context, alarm),
            windowMillis = WINDOW_MS,
            allowInexact = true,
        )
        Log.d(TAG, "Alarm scheduled for ${java.util.Date(triggerAt)}")
    }

    fun cancel(
        context: Context,
        alarm: Alarm,
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntentFor(context, alarm))
        Log.d(TAG, "Alarm cancelled")
    }

    private fun nextOccurrence(alarm: Alarm): Long {
        val calendar =
            Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, alarm.hour)
                set(Calendar.MINUTE, alarm.minute)
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
        alarm: Alarm,
    ): PendingIntent {
        val intent =
            Intent(context, AlarmReceiver::class.java)
                .setAction(AlarmReceiver.ACTION_ALARM)
        return PendingIntent.getBroadcast(
            context,
            alarm.requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
