package com.rober.photoframe.data

import android.content.Context
import android.content.SharedPreferences

/**
 * The user's morning alarm.
 *
 * Formerly named `AlarmManager`, which sat in the same files as `android.app.AlarmManager`
 * and made every reference ambiguous to read.
 */
data class Alarm(
    val hour: Int,
    val minute: Int,
    val enabled: Boolean = true,
    val label: String = "Morning Alarm",
) {
    /** Stable request code for the scheduled PendingIntent. */
    val requestCode: Int get() = ID.hashCode()

    companion object {
        const val ID = "main_alarm"
    }
}

object AlarmSettings {

    private const val PREF_NAME = "alarm_prefs"
    private const val KEY_HOUR = "alarm_hour"
    private const val KEY_MINUTE = "alarm_minute"
    private const val KEY_ENABLED = "alarm_enabled"
    private const val KEY_LABEL = "alarm_label"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun get(): Alarm? {
        val hour = prefs.getInt(KEY_HOUR, -1)
        if (hour !in 0..23) return null

        return Alarm(
            hour = hour,
            minute = prefs.getInt(KEY_MINUTE, 0),
            enabled = prefs.getBoolean(KEY_ENABLED, true),
            label = prefs.getString(KEY_LABEL, "Morning Alarm") ?: "Morning Alarm",
        )
    }

    fun save(alarm: Alarm) {
        prefs.edit()
            .putInt(KEY_HOUR, alarm.hour)
            .putInt(KEY_MINUTE, alarm.minute)
            .putBoolean(KEY_ENABLED, alarm.enabled)
            .putString(KEY_LABEL, alarm.label)
            .commit()
    }

    fun clear() {
        prefs.edit().clear().commit()
    }
}
