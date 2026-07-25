package com.rober.photoframe.settings

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri

object PhotoframePreferences {
    private const val PREF_NAME = "photoframe_prefs"
    
    // Keys
    private const val KEY_SLIDE_INTERVAL = "slide_interval"
    private const val KEY_SHUFFLE = "shuffle"
    private const val KEY_INCLUDE_VIDEOS = "include_videos"
    private const val KEY_GALLERY_URI = "gallery_uri"
    private const val KEY_TRANSITION_EFFECT = "transition_effect"
    private const val KEY_CLOCK_STYLE = "clock_style"
    private const val KEY_AUTO_START = "auto_start"
    private const val KEY_VIDEO_SOUND = "video_sound"
    private const val KEY_WAKE_TIME = "wake_time"
    private const val KEY_SLEEP_TIME = "sleep_time"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    // Basic Settings
    var slideIntervalSeconds: Int
        get() = prefs.getInt(KEY_SLIDE_INTERVAL, 10)
        set(value) = prefs.edit().putInt(KEY_SLIDE_INTERVAL, value).commit().let { }

    var shuffle: Boolean
        get() = prefs.getBoolean(KEY_SHUFFLE, false)
        set(value) = prefs.edit().putBoolean(KEY_SHUFFLE, value).commit().let { }

    var includeVideos: Boolean
        get() = prefs.getBoolean(KEY_INCLUDE_VIDEOS, true)
        set(value) = prefs.edit().putBoolean(KEY_INCLUDE_VIDEOS, value).commit().let { }

    var galleryUriString: String?
        get() = prefs.getString(KEY_GALLERY_URI, null)
        set(value) = prefs.edit().putString(KEY_GALLERY_URI, value).commit().let { }

    // Advanced Settings
    var transitionEffect: String
        get() = prefs.getString(KEY_TRANSITION_EFFECT, "FADE") ?: "FADE"
        set(value) = prefs.edit().putString(KEY_TRANSITION_EFFECT, value).commit().let { }

    var clockStyle: String
        get() = prefs.getString(KEY_CLOCK_STYLE, "DIGITAL") ?: "DIGITAL"
        set(value) = prefs.edit().putString(KEY_CLOCK_STYLE, value).commit().let { }

    var autoStartOnBoot: Boolean
        get() = prefs.getBoolean(KEY_AUTO_START, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_START, value).commit().let { }

    var videoSoundEnabled: Boolean
        get() = prefs.getBoolean(KEY_VIDEO_SOUND, false)
        set(value) = prefs.edit().putBoolean(KEY_VIDEO_SOUND, value).commit().let { }

    var wakeTimeMinutes: Int
        get() = prefs.getInt(KEY_WAKE_TIME, -1)
        set(value) = prefs.edit().putInt(KEY_WAKE_TIME, value).commit().let { }

    var wakeTime: String?
        get() = if (wakeTimeMinutes == -1) null else String.format("%02d:%02d", wakeTimeMinutes / 60, wakeTimeMinutes % 60)
        set(value) {
            wakeTimeMinutes = if (value.isNullOrEmpty()) -1 else {
                try {
                    val parts = value.split(":")
                    parts[0].toInt() * 60 + parts[1].toInt()
                } catch (e: Exception) {
                    -1
                }
            }
        }

    var sleepTimeMinutes: Int
        get() = prefs.getInt(KEY_SLEEP_TIME, -1)
        set(value) = prefs.edit().putInt(KEY_SLEEP_TIME, value).commit().let { }

    var sleepTime: String?
        get() = if (sleepTimeMinutes == -1) null else String.format("%02d:%02d", sleepTimeMinutes / 60, sleepTimeMinutes % 60)
        set(value) {
            sleepTimeMinutes = if (value.isNullOrEmpty()) -1 else {
                try {
                    val parts = value.split(":")
                    parts[0].toInt() * 60 + parts[1].toInt()
                } catch (e: Exception) {
                    -1
                }
            }
        }
}
