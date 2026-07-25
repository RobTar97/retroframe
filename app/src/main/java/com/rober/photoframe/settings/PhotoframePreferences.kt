package com.rober.photoframe.settings

import android.content.Context
import android.content.SharedPreferences

/**
 * Every user-facing setting.
 *
 * Writes use `commit()` rather than `apply()`. This is deliberate and should be preserved:
 * a photo frame is a device that loses power abruptly, and an asynchronous write that has
 * not reached disk is a setting the user has to enter again. The cost is a single
 * synchronous disk write on a settings screen the user visits rarely.
 */
object PhotoframePreferences {

    private const val PREF_NAME = "photoframe_prefs"

    private const val KEY_SLIDE_INTERVAL = "slide_interval"
    private const val KEY_SHUFFLE = "shuffle"
    private const val KEY_INCLUDE_VIDEOS = "include_videos"
    private const val KEY_GALLERY_URI = "gallery_uri"
    private const val KEY_TRANSITION = "transition_effect"
    private const val KEY_AUTO_START = "auto_start"
    private const val KEY_VIDEO_SOUND = "video_sound"
    private const val KEY_WAKE_TIME = "wake_time"
    private const val KEY_SLEEP_TIME = "sleep_time"
    private const val KEY_KEEP_SCREEN_ON = "keep_screen_on"

    const val MIN_INTERVAL_SECONDS = 5
    const val MAX_INTERVAL_SECONDS = 3600
    const val DEFAULT_INTERVAL_SECONDS = 10

    /** Sentinel for "no time set". */
    const val TIME_DISABLED = -1

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    var slideIntervalSeconds: Int
        get() = prefs.getInt(KEY_SLIDE_INTERVAL, DEFAULT_INTERVAL_SECONDS)
        set(value) {
            prefs.edit()
                .putInt(KEY_SLIDE_INTERVAL, value.coerceIn(MIN_INTERVAL_SECONDS, MAX_INTERVAL_SECONDS))
                .commit()
        }

    var shuffle: Boolean
        get() = prefs.getBoolean(KEY_SHUFFLE, true)
        set(value) { prefs.edit().putBoolean(KEY_SHUFFLE, value).commit() }

    var includeVideos: Boolean
        get() = prefs.getBoolean(KEY_INCLUDE_VIDEOS, true)
        set(value) { prefs.edit().putBoolean(KEY_INCLUDE_VIDEOS, value).commit() }

    var galleryUriString: String?
        get() = prefs.getString(KEY_GALLERY_URI, null)
        set(value) { prefs.edit().putString(KEY_GALLERY_URI, value).commit() }

    var transitionEffect: TransitionEffect
        get() = TransitionEffect.fromKey(prefs.getString(KEY_TRANSITION, null))
        set(value) { prefs.edit().putString(KEY_TRANSITION, value.key).commit() }

    var autoStartOnBoot: Boolean
        get() = prefs.getBoolean(KEY_AUTO_START, false)
        set(value) { prefs.edit().putBoolean(KEY_AUTO_START, value).commit() }

    var videoSoundEnabled: Boolean
        get() = prefs.getBoolean(KEY_VIDEO_SOUND, false)
        set(value) { prefs.edit().putBoolean(KEY_VIDEO_SOUND, value).commit() }

    /** Whether photo mode holds the screen awake. Off lets the device time out normally. */
    var keepScreenOn: Boolean
        get() = prefs.getBoolean(KEY_KEEP_SCREEN_ON, true)
        set(value) { prefs.edit().putBoolean(KEY_KEEP_SCREEN_ON, value).commit() }

    /** Minutes since midnight, or [TIME_DISABLED]. */
    var wakeTimeMinutes: Int
        get() = prefs.getInt(KEY_WAKE_TIME, TIME_DISABLED)
        set(value) { prefs.edit().putInt(KEY_WAKE_TIME, value).commit() }

    var sleepTimeMinutes: Int
        get() = prefs.getInt(KEY_SLEEP_TIME, TIME_DISABLED)
        set(value) { prefs.edit().putInt(KEY_SLEEP_TIME, value).commit() }

    var wakeTime: String?
        get() = TimeOfDay.format(wakeTimeMinutes)
        set(value) { wakeTimeMinutes = TimeOfDay.parse(value) }

    var sleepTime: String?
        get() = TimeOfDay.format(sleepTimeMinutes)
        set(value) { sleepTimeMinutes = TimeOfDay.parse(value) }
}

/**
 * Parsing and formatting for `HH:mm` settings.
 *
 * Pure and Android-free so the edge cases — empty input, "7:5", "25:00", "not a time" —
 * are unit tested rather than discovered on a tablet.
 */
object TimeOfDay {

    fun parse(value: String?): Int {
        val text = value?.trim().orEmpty()
        if (text.isEmpty()) return PhotoframePreferences.TIME_DISABLED

        val parts = text.split(":")
        if (parts.size != 2) return PhotoframePreferences.TIME_DISABLED

        val hour = parts[0].trim().toIntOrNull() ?: return PhotoframePreferences.TIME_DISABLED
        val minute = parts[1].trim().toIntOrNull() ?: return PhotoframePreferences.TIME_DISABLED

        if (hour !in 0..23 || minute !in 0..59) return PhotoframePreferences.TIME_DISABLED
        return hour * 60 + minute
    }

    fun format(minutesSinceMidnight: Int): String? {
        if (minutesSinceMidnight < 0 || minutesSinceMidnight >= 24 * 60) return null
        return "%02d:%02d".format(minutesSinceMidnight / 60, minutesSinceMidnight % 60)
    }
}

/** Page transition used between slides. */
enum class TransitionEffect(val key: String) {
    FADE("FADE"),
    SLIDE("SLIDE"),
    ZOOM("ZOOM"),
    ;

    companion object {
        fun fromKey(key: String?): TransitionEffect =
            entries.firstOrNull { it.key == key } ?: FADE
    }
}
