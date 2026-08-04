package com.rober.photoframe

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import com.rober.photoframe.settings.Brightness
import com.rober.photoframe.settings.PhotoframePreferences
import com.rober.photoframe.ui.ClockFragment
import com.rober.photoframe.ui.SlideshowFragment

/**
 * Single activity hosting the two modes.
 *
 * The distinction that matters is which mode is allowed to let the screen turn off:
 * photo mode holds it awake, clock mode releases it so the device's own timeout applies.
 */
class MainActivity : AppCompatActivity(R.layout.activity_main) {
    private val notificationPermission =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { /* The alarm still fires without it; only the notification is suppressed. */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        applyImmersiveMode()

        if (savedInstanceState == null) {
            val mode = intent.getStringExtra(EXTRA_MODE)
            if (mode == MODE_CLOCK) switchToClockMode() else switchToPhotoMode()
        } else {
            // Window flags do not survive the activity being recreated, and neither switch
            // method runs on the restore path — so after a rotation or a system font change
            // the frame would quietly stop holding the screen awake, and clock mode would
            // come back at full brightness.
            applyModeFlags(
                clockMode = supportFragmentManager.findFragmentById(R.id.container)
                    is ClockFragment,
            )
        }

        requestNotificationPermissionIfNeeded()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        when (intent.getStringExtra(EXTRA_MODE)) {
            MODE_CLOCK -> switchToClockMode()
            MODE_PHOTO -> switchToPhotoMode()
        }
    }

    /**
     * Screen taps are detected here, at the very top of the dispatch chain.
     *
     * Lower layers cannot see them reliably: PhotoView calls
     * `requestDisallowInterceptTouchEvent(true)` so it can own pinch and pan, which cuts the
     * ViewPager's RecyclerView out of the touch stream entirely, and PhotoView's own tap
     * callbacks do not fire for taps inside the displayed image. The result was that tapping
     * a photo — nearly every tap a user makes — did nothing at all, and the controls could
     * only be summoned from the black margins beside the picture.
     *
     * `dispatchTouchEvent` runs before any of that, so it always sees the gesture. Events are
     * observed and passed straight on, so swiping, zooming and the control buttons are
     * unaffected.
     */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        screenTaps.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    private val screenTaps by lazy {
        GestureDetector(
            this,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    (supportFragmentManager.findFragmentById(R.id.container) as? ScreenTapListener)
                        ?.onScreenTapped(e.rawX.toInt(), e.rawY.toInt())
                    return false
                }
            },
        )
    }

    /** Implemented by whichever fragment wants to know about bare screen taps. */
    interface ScreenTapListener {
        fun onScreenTapped(
            rawX: Int,
            rawY: Int,
        )
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // System bars reappear after dialogs, notification shade pulls and permission
        // prompts; re-hide them whenever focus comes back.
        if (hasFocus) applyImmersiveMode()
    }

    // ------------------------------------------------------------------ modes

    fun switchToPhotoMode() {
        applyModeFlags(clockMode = false)
        show(SlideshowFragment())
    }

    fun switchToClockMode() {
        applyModeFlags(clockMode = true)
        show(ClockFragment())
    }

    /** The two window properties that differ between the modes. */
    private fun applyModeFlags(clockMode: Boolean) {
        setKeepScreenOn(!clockMode && PhotoframePreferences.keepScreenOn)
        applyBrightness(
            if (clockMode) {
                Brightness.toWindowValue(PhotoframePreferences.nightBrightnessPercent)
            } else {
                Brightness.OVERRIDE_NONE
            },
        )
    }

    private fun show(fragment: Fragment) {
        // Guard against re-entering the same mode, which would needlessly tear down and
        // rebuild the slideshow (and rescan the folder) when an alarm re-fires.
        val current = supportFragmentManager.findFragmentById(R.id.container)
        if (current != null && current::class == fragment::class) return

        supportFragmentManager.commit {
            setReorderingAllowed(true)
            replace(R.id.container, fragment)
        }
    }

    /**
     * Dims the panel for clock mode.
     *
     * This is a *window* brightness override, not a system setting: it needs no permission,
     * applies only while RetroFrame is in front, and is undone by Android the moment the app
     * loses focus. That matters for a device on a bedside table — nothing this app does can
     * leave the tablet stuck at 5% for its owner to puzzle over later.
     *
     * [Brightness.OVERRIDE_NONE] hands control back to the system.
     */
    private fun applyBrightness(value: Float) {
        window.attributes =
            window.attributes.apply { screenBrightness = value }
    }

    private fun setKeepScreenOn(enabled: Boolean) {
        if (enabled) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // ------------------------------------------------------------- system UI

    /**
     * Full immersive mode.
     *
     * Targeting API 35+ makes edge-to-edge mandatory and ignores the old
     * `android:windowFullscreen` theme attribute, so the layout is driven entirely through
     * [WindowInsetsControllerCompat] here. That suits this app: a photo frame wants the
     * photo bleeding to every edge of the panel anyway.
     */
    private fun applyImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        val granted =
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED

        // Only worth asking if the user actually has an alarm configured.
        if (!granted && com.rober.photoframe.data.AlarmSettings.get()?.enabled == true) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    companion object {
        const val EXTRA_MODE = "MODE"
        const val MODE_PHOTO = "PHOTO"
        const val MODE_CLOCK = "CLOCK"
    }
}
