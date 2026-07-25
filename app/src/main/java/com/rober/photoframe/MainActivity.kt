package com.rober.photoframe

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
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

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* The alarm still fires without it; only the notification is suppressed. */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        applyImmersiveMode()

        if (savedInstanceState == null) {
            val mode = intent.getStringExtra(EXTRA_MODE)
            if (mode == MODE_CLOCK) switchToClockMode() else switchToPhotoMode()
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

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // System bars reappear after dialogs, notification shade pulls and permission
        // prompts; re-hide them whenever focus comes back.
        if (hasFocus) applyImmersiveMode()
    }

    // ------------------------------------------------------------------ modes

    fun switchToPhotoMode() {
        setKeepScreenOn(PhotoframePreferences.keepScreenOn)
        show(SlideshowFragment())
    }

    fun switchToClockMode() {
        setKeepScreenOn(false)
        show(ClockFragment())
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

        val granted = ContextCompat.checkSelfPermission(
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
