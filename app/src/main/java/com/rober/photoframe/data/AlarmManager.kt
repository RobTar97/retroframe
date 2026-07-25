package com.rober.photoframe.data

import android.content.Context
import android.content.SharedPreferences

data class Alarm(
    val id: String,
    val hour: Int,
    val minute: Int,
    val enabled: Boolean = true,
    val label: String = "Morning Alarm"
)

object AlarmManager {
    private const val PREF_NAME = "alarm_prefs"
    private const val KEY_ALARM_HOUR = "alarm_hour"
    private const val KEY_ALARM_MINUTE = "alarm_minute"
    private const val KEY_ALARM_ENABLED = "alarm_enabled"
    private const val KEY_ALARM_LABEL = "alarm_label"
    
    private lateinit var prefs: SharedPreferences
    
    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }
    
    fun getAlarm(): Alarm? {
        val hour = prefs.getInt(KEY_ALARM_HOUR, -1)
        if (hour == -1) return null
        
        return Alarm(
            id = "main_alarm",
            hour = hour,
            minute = prefs.getInt(KEY_ALARM_MINUTE, 0),
            enabled = prefs.getBoolean(KEY_ALARM_ENABLED, true),
            label = prefs.getString(KEY_ALARM_LABEL, "Morning Alarm") ?: "Morning Alarm"
        )
    }
    
    fun saveAlarm(alarm: Alarm) {
        prefs.edit().apply {
            putInt(KEY_ALARM_HOUR, alarm.hour)
            putInt(KEY_ALARM_MINUTE, alarm.minute)
            putBoolean(KEY_ALARM_ENABLED, alarm.enabled)
            putString(KEY_ALARM_LABEL, alarm.label)
            apply()
        }
    }
    
    fun deleteAlarm() {
        prefs.edit().clear().apply()
    }
    
    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ALARM_ENABLED, enabled).apply()
    }
}
