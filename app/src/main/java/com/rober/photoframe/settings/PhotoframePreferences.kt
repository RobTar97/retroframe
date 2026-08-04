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
    private const val KEY_SEEN_WELCOME = "seen_welcome"
    private const val KEY_INCLUDE_SUBFOLDERS = "include_subfolders"
    private const val KEY_NIGHT_BRIGHTNESS = "night_brightness"
    private const val KEY_BURN_IN_PROTECTION = "burn_in_protection"

    const val MIN_INTERVAL_SECONDS = 5
    const val MAX_INTERVAL_SECONDS = 3600
    const val DEFAULT_INTERVAL_SECONDS = 10

    /** Sentinel for "no time set". */
    const val TIME_DISABLED = -1

    /** Sentinel for "leave the screen at whatever the system decided". */
    const val BRIGHTNESS_SYSTEM = -1

    /**
     * The floor for night dimming, as a percentage.
     *
     * Zero is a legal value for `screenBrightness`, but on a good many panels it means the
     * backlight is off entirely — a frame the user cannot see and cannot obviously fix,
     * because the tap-to-wake controls are invisible too. 5% is dim enough for a dark
     * bedroom and still leaves something on screen.
     */
    const val MIN_NIGHT_BRIGHTNESS = 5

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    var slideIntervalSeconds: Int
        get() = prefs.getInt(KEY_SLIDE_INTERVAL, DEFAULT_INTERVAL_SECONDS)
        set(value) {
            prefs.edit()
                .putInt(
                    KEY_SLIDE_INTERVAL,
                    value.coerceIn(MIN_INTERVAL_SECONDS, MAX_INTERVAL_SECONDS),
                )
                .commit()
        }

    var shuffle: Boolean
        get() = prefs.getBoolean(KEY_SHUFFLE, true)
        set(value) {
            prefs.edit().putBoolean(KEY_SHUFFLE, value).commit()
        }

    var includeVideos: Boolean
        get() = prefs.getBoolean(KEY_INCLUDE_VIDEOS, true)
        set(value) {
            prefs.edit().putBoolean(KEY_INCLUDE_VIDEOS, value).commit()
        }

    /**
     * Whether the scan descends into folders inside the chosen one.
     *
     * Defaults to on. People organise photos into `2019/`, `Holidays/`, `Kids/` and then point
     * the frame at the folder above; before this existed they got an empty screen and no clue
     * why. "More photos than I expected" is a far kinder failure than "none of my photos
     * appear", so the surprising default is the safe one.
     */
    var includeSubfolders: Boolean
        get() = prefs.getBoolean(KEY_INCLUDE_SUBFOLDERS, true)
        set(value) {
            prefs.edit().putBoolean(KEY_INCLUDE_SUBFOLDERS, value).commit()
        }

    /**
     * Screen brightness during clock/sleep mode, as a percentage, or [BRIGHTNESS_SYSTEM].
     *
     * Stored as a percentage rather than the float the window API wants, because a percentage
     * survives being read back into a slider without rounding drift.
     */
    var nightBrightnessPercent: Int
        get() = prefs.getInt(KEY_NIGHT_BRIGHTNESS, BRIGHTNESS_SYSTEM)
        set(value) {
            val stored =
                if (value < MIN_NIGHT_BRIGHTNESS) BRIGHTNESS_SYSTEM else value.coerceAtMost(100)
            prefs.edit().putInt(KEY_NIGHT_BRIGHTNESS, stored).commit()
        }

    /** Whether static overlays drift slowly to avoid marking the panel. */
    var burnInProtection: Boolean
        get() = prefs.getBoolean(KEY_BURN_IN_PROTECTION, true)
        set(value) {
            prefs.edit().putBoolean(KEY_BURN_IN_PROTECTION, value).commit()
        }

    var galleryUriString: String?
        get() = prefs.getString(KEY_GALLERY_URI, null)
        set(value) {
            prefs.edit().putString(KEY_GALLERY_URI, value).commit()
        }

    var transitionEffect: TransitionEffect
        get() = TransitionEffect.fromKey(prefs.getString(KEY_TRANSITION, null))
        set(value) {
            prefs.edit().putString(KEY_TRANSITION, value.key).commit()
        }

    var autoStartOnBoot: Boolean
        get() = prefs.getBoolean(KEY_AUTO_START, false)
        set(value) {
            prefs.edit().putBoolean(KEY_AUTO_START, value).commit()
        }

    var videoSoundEnabled: Boolean
        get() = prefs.getBoolean(KEY_VIDEO_SOUND, false)
        set(value) {
            prefs.edit().putBoolean(KEY_VIDEO_SOUND, value).commit()
        }

    /** The first-run explanation is shown once, before the folder picker. */
    var hasSeenWelcome: Boolean
        get() = prefs.getBoolean(KEY_SEEN_WELCOME, false)
        set(value) {
            prefs.edit().putBoolean(KEY_SEEN_WELCOME, value).commit()
        }

    /** Whether photo mode holds the screen awake. Off lets the device time out normally. */
    var keepScreenOn: Boolean
        get() = prefs.getBoolean(KEY_KEEP_SCREEN_ON, true)
        set(value) {
            prefs.edit().putBoolean(KEY_KEEP_SCREEN_ON, value).commit()
        }

    /** Minutes since midnight, or [TIME_DISABLED]. */
    var wakeTimeMinutes: Int
        get() = prefs.getInt(KEY_WAKE_TIME, TIME_DISABLED)
        set(value) {
            prefs.edit().putInt(KEY_WAKE_TIME, value).commit()
        }

    var sleepTimeMinutes: Int
        get() = prefs.getInt(KEY_SLEEP_TIME, TIME_DISABLED)
        set(value) {
            prefs.edit().putInt(KEY_SLEEP_TIME, value).commit()
        }

    var wakeTime: String?
        get() = TimeOfDay.format(wakeTimeMinutes)
        set(value) {
            wakeTimeMinutes = TimeOfDay.parse(value)
        }

    var sleepTime: String?
        get() = TimeOfDay.format(sleepTimeMinutes)
        set(value) {
            sleepTimeMinutes = TimeOfDay.parse(value)
        }
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

/**
 * Converts the stored night-brightness percentage into what `WindowManager.LayoutParams`
 * wants.
 *
 * Pure, and deliberately separate from the Activity, because the one thing that can go badly
 * wrong here — handing the window a value low enough to black the panel out — is worth a unit
 * test rather than a trip to a dark room with a tablet.
 */
object Brightness {
    /** `WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE`, without the Android import. */
    const val OVERRIDE_NONE = -1.0f

    fun toWindowValue(percent: Int): Float {
        if (percent < PhotoframePreferences.MIN_NIGHT_BRIGHTNESS) return OVERRIDE_NONE
        return (percent.coerceAtMost(100) / 100f)
            .coerceAtLeast(PhotoframePreferences.MIN_NIGHT_BRIGHTNESS / 100f)
    }
}

/** Page transition used between slides. */
enum class TransitionEffect(val key: String) {
    FADE("FADE"),
    SLIDE("SLIDE"),
    ZOOM("ZOOM"),
    ;

    companion object {
        fun fromKey(key: String?): TransitionEffect = entries.firstOrNull { it.key == key } ?: FADE
    }
}
