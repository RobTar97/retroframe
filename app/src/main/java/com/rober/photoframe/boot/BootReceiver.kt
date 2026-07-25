package com.rober.photoframe.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.rober.photoframe.MainActivity
import com.rober.photoframe.alarm.AlarmScheduler
import com.rober.photoframe.data.AlarmSettings
import com.rober.photoframe.settings.PhotoframePreferences
import com.rober.photoframe.util.ScreenManager

/**
 * Restores the schedule after a reboot.
 *
 * Android drops all pending alarms on shutdown, so without this the wake/sleep times and the
 * morning alarm would silently stop working after the first power cut — the exact scenario an
 * unattended photo frame runs into.
 */
class BootReceiver : BroadcastReceiver() {

    private companion object {
        const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED &&
            action != "android.intent.action.QUICKBOOT_POWERON"
        ) {
            return
        }

        // A receiver can run before anything else has touched these singletons.
        PhotoframePreferences.init(context)
        AlarmSettings.init(context)

        ScreenManager.scheduleAlarms(context)
        AlarmSettings.get()?.takeIf { it.enabled }?.let { AlarmScheduler.schedule(context, it) }

        if (!PhotoframePreferences.autoStartOnBoot) return

        try {
            context.startActivity(
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(MainActivity.EXTRA_MODE, MainActivity.MODE_PHOTO),
            )
        } catch (e: Exception) {
            // Newer Android versions restrict launching activities from the background.
            Log.w(TAG, "Auto-start on boot was blocked by the system", e)
        }
    }
}
