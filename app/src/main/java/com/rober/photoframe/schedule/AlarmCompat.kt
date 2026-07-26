package com.rober.photoframe.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.os.Build
import android.util.Log

/**
 * Version-safe alarm scheduling.
 *
 * `setExactAndAllowWhileIdle` was added in API 23, but RetroFrame supports API 22 — so the
 * previous code would have thrown `NoSuchMethodError` on Android 5.1, the oldest version the
 * app claims to run on. Nobody noticed because the crash only happens on the exact hardware
 * that is hardest to test on.
 *
 * This picks the best available API for the running device:
 *
 * | API   | Method                    | Doze-proof         |
 * |-------|---------------------------|--------------------|
 * | 23+   | setExactAndAllowWhileIdle | yes                |
 * | 22    | setExact                  | n/a — no Doze yet  |
 *
 * There is no branch below API 22 because that is the app's minimum. Doze arrived with
 * API 23, so on 22 there is nothing for an alarm to be held back by in the first place.
 */
object AlarmCompat {
    private const val TAG = "AlarmCompat"

    /**
     * @param allowInexact when true and exact alarms are unavailable, falls back to a
     *   windowed alarm rather than not scheduling at all.
     */
    fun schedule(
        alarmManager: AlarmManager,
        triggerAtMillis: Long,
        pendingIntent: PendingIntent,
        windowMillis: Long,
        allowInexact: Boolean = true,
    ) {
        val exactPermitted =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                alarmManager.canScheduleExactAlarms()

        try {
            when {
                exactPermitted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ->
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent,
                    )

                // API 22 only. setExactAndAllowWhileIdle does not exist here, which is the
                // crash this class was written to prevent.
                exactPermitted ->
                    alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pendingIntent,
                    )

                allowInexact ->
                    alarmManager.setWindow(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        windowMillis,
                        pendingIntent,
                    )

                else -> Log.w(TAG, "Exact alarms not permitted and inexact not allowed")
            }
        } catch (e: SecurityException) {
            // The permission can be revoked between the check above and this call.
            Log.e(TAG, "Denied while scheduling; retrying inexact", e)
            if (allowInexact) {
                try {
                    alarmManager.setWindow(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        windowMillis,
                        pendingIntent,
                    )
                } catch (retry: SecurityException) {
                    Log.e(TAG, "Could not schedule the alarm at all", retry)
                }
            }
        }
    }
}
