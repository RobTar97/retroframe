package com.rober.photoframe.schedule

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import com.rober.photoframe.MainActivity
import com.rober.photoframe.R
import com.rober.photoframe.data.AlarmSettings

class AlarmReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_ALARM = "com.rober.photoframe.ACTION_ALARM"
        const val CHANNEL_ID = "alarm_channel"
        const val NOTIFICATION_ID = 1001
    }

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != ACTION_ALARM) return

        AlarmSettings.init(context)
        val alarm = AlarmSettings.get()

        if (alarm != null && alarm.enabled) {
            showNotification(context, alarm.label)
            // Re-arm for tomorrow.
            AlarmScheduler.schedule(context, alarm)
        }
    }

    private fun showNotification(
        context: Context,
        label: String,
    ) {
        createChannel(context)

        val openIntent =
            Intent(context, MainActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                .putExtra(MainActivity.EXTRA_MODE, MainActivity.MODE_PHOTO)

        val openPendingIntent =
            PendingIntent.getActivity(
                context,
                0,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val dismissPendingIntent =
            PendingIntent.getBroadcast(
                context,
                1,
                Intent(context, AlarmDismissReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val notification =
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_clock)
                .setContentTitle(label)
                .setContentText(context.getString(R.string.alarm_time_to_wake))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
                // Full-screen intent is what makes an alarm show over the lock screen, which is
                // exactly the situation a bedside photo frame is in at 07:00.
                .setFullScreenIntent(openPendingIntent, true)
                .setContentIntent(openPendingIntent)
                .addAction(
                    R.drawable.ic_clock,
                    context.getString(R.string.dismiss),
                    dismissPendingIntent,
                )
                .build()

        try {
            context.getSystemService<NotificationManager>()
                ?.notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS denied on Android 13+. Nothing else to do here.
        }
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel =
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.alarm_channel_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.alarm_channel_description)
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        // USAGE_ALARM plays at alarm volume and ignores Do Not Disturb, which is
                        // what a user setting a wake-up alarm expects.
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .build(),
                )
            }

        context.getSystemService<NotificationManager>()?.createNotificationChannel(channel)
    }
}

class AlarmDismissReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        context.getSystemService<NotificationManager>()
            ?.cancel(AlarmReceiver.NOTIFICATION_ID)
    }
}
