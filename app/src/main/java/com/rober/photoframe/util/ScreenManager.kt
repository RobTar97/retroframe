package com.rober.photoframe.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.util.Log
import com.rober.photoframe.settings.PhotoframePreferences
import java.util.Calendar

object ScreenManager {

    private const val TAG = "ScreenManager"
    private const val ACTION_WAKE_UP = "com.rober.photoframe.ACTION_WAKE_UP"
    private const val ACTION_SLEEP = "com.rober.photoframe.ACTION_SLEEP"
    private const val WAKELOCK_TAG = "Photoframe:WakeUpLock"

    fun scheduleAlarms(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        
        // Check for exact alarm permission on Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                Log.w(TAG, "Cannot schedule exact alarms")
                return
            }
        }

        val wakeTime = PhotoframePreferences.wakeTimeMinutes
        val sleepTime = PhotoframePreferences.sleepTimeMinutes

        cancelAlarms(context)

        if (wakeTime != -1) {
            scheduleAlarm(context, alarmManager, wakeTime, ACTION_WAKE_UP)
        }

        if (sleepTime != -1) {
            scheduleAlarm(context, alarmManager, sleepTime, ACTION_SLEEP)
        }
    }

    private fun scheduleAlarm(context: Context, alarmManager: AlarmManager, timeMinutes: Int, action: String) {
        val hour = timeMinutes / 60
        val minute = timeMinutes % 60

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        if (calendar.timeInMillis <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            this.action = action
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            context, 
            action.hashCode(), 
            intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
            Log.d(TAG, "Scheduled $action for ${calendar.time}")
        } catch (e: SecurityException) {
            Log.e(TAG, "Failed to schedule alarm", e)
        }
    }

    private fun cancelAlarms(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        
        val wakeIntent = Intent(context, AlarmReceiver::class.java).apply { action = ACTION_WAKE_UP }
        val sleepIntent = Intent(context, AlarmReceiver::class.java).apply { action = ACTION_SLEEP }

        val wakePending = PendingIntent.getBroadcast(context, ACTION_WAKE_UP.hashCode(), wakeIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val sleepPending = PendingIntent.getBroadcast(context, ACTION_SLEEP.hashCode(), sleepIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        alarmManager.cancel(wakePending)
        alarmManager.cancel(sleepPending)
    }

    class AlarmReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d(TAG, "Received alarm: ${intent.action}")
            
            when (intent.action) {
                ACTION_WAKE_UP -> {
                    // Acquire a WakeLock to wake up the device and turn on screen
                    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                    val wakeLock = powerManager.newWakeLock(
                        PowerManager.SCREEN_BRIGHT_WAKE_LOCK or 
                        PowerManager.ACQUIRE_CAUSES_WAKEUP or
                        PowerManager.ON_AFTER_RELEASE,
                        WAKELOCK_TAG
                    )
                    
                    try {
                        // Acquire wake lock for 3 seconds to ensure screen turns on
                        wakeLock.acquire(3000L)
                        
                        // Turn screen on and launch app in photo mode
                        val launchIntent = Intent(context, com.rober.photoframe.MainActivity::class.java)
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                        launchIntent.putExtra("MODE", "PHOTO")
                        context.startActivity(launchIntent)
                        
                        Log.d(TAG, "Wake up alarm triggered - launching photo mode")
                    } finally {
                        // Release wake lock after a delay (system will handle if already released)
                        if (wakeLock.isHeld) {
                            wakeLock.release()
                        }
                    }
                }
                ACTION_SLEEP -> {
                    // Switch to clock mode (screen will be allowed to turn off)
                    val launchIntent = Intent(context, com.rober.photoframe.MainActivity::class.java)
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    launchIntent.putExtra("MODE", "CLOCK")
                    context.startActivity(launchIntent)
                    
                    Log.d(TAG, "Sleep alarm triggered - switching to clock mode")
                }
            }
            
            // Reschedule for next day
            scheduleAlarms(context)
        }
    }
}
