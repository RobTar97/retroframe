package com.rober.photoframe.ui

import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.fragment.app.Fragment
import com.rober.photoframe.MainActivity
import com.rober.photoframe.R

/**
 * Fullscreen clock.
 *
 * This is what the sleep schedule switches to. The activity clears FLAG_KEEP_SCREEN_ON on
 * the way in, so the device's own display timeout takes over and the screen actually turns
 * off — which is the entire point of having a sleep time.
 */
class ClockFragment : Fragment(R.layout.fragment_clock) {

    private lateinit var btnBackToPhotos: Button

    private val hideButton = Runnable { btnBackToPhotos.visibility = View.GONE }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        btnBackToPhotos = view.findViewById(R.id.btnBackToPhotos)

        view.setOnClickListener {
            if (btnBackToPhotos.visibility == View.VISIBLE) hide() else show()
        }

        btnBackToPhotos.setOnClickListener {
            (activity as? MainActivity)?.switchToPhotoMode()
        }
    }

    private fun show() {
        btnBackToPhotos.removeCallbacks(hideButton)
        btnBackToPhotos.visibility = View.VISIBLE
        btnBackToPhotos.postDelayed(hideButton, BUTTON_TIMEOUT_MS)
    }

    private fun hide() {
        btnBackToPhotos.removeCallbacks(hideButton)
        btnBackToPhotos.visibility = View.GONE
    }

    override fun onDestroyView() {
        btnBackToPhotos.removeCallbacks(hideButton)
        super.onDestroyView()
    }

    private companion object {
        const val BUTTON_TIMEOUT_MS = 3_000L
    }
}
