package com.rober.photoframe

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.rober.photoframe.ui.SlideshowFragment

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        hideSystemUI()

        if (savedInstanceState == null) {
            // Check if we should start in clock mode
            val mode = intent.getStringExtra("MODE")
            if (mode == "CLOCK") {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.container, com.rober.photoframe.ui.ClockFragment())
                    .commit()
                disableKeepScreenOn()
            } else {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.container, SlideshowFragment())
                    .commit()
                enableKeepScreenOn()
            }
        }

        checkPermissions()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Handle when activity is already running and receives new intent
        intent?.let {
            val mode = it.getStringExtra("MODE")
            if (mode == "CLOCK") {
                switchToClockMode()
            } else if (mode == "PHOTO") {
                switchToPhotoMode()
            }
        }
    }

    private fun hideSystemUI() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
    
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemUI()
        }
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
             if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                 // Request notification permission if needed
             }
        }
    }
    
    /**
     * Enable screen keep-on for photo mode
     */
    fun enableKeepScreenOn() {
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
    
    /**
     * Disable screen keep-on to allow sleep during clock mode
     */
    fun disableKeepScreenOn() {
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
    
    fun switchToClockMode() {
        disableKeepScreenOn()
        supportFragmentManager.beginTransaction()
            .replace(R.id.container, com.rober.photoframe.ui.ClockFragment())
            .commit()
    }
    
    fun switchToPhotoMode() {
        enableKeepScreenOn()
        supportFragmentManager.beginTransaction()
            .replace(R.id.container, SlideshowFragment())
            .commit()
    }
}
