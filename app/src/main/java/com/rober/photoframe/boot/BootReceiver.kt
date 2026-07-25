package com.rober.photoframe.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.rober.photoframe.settings.PhotoframePreferences
import com.rober.photoframe.util.ScreenManager

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            PhotoframePreferences.init(context)
            
            // Reschedule alarms
            ScreenManager.scheduleAlarms(context)

            // Auto-start if enabled
            if (PhotoframePreferences.autoStartOnBoot) {
                val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                launchIntent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
            }
        }
    }
}
