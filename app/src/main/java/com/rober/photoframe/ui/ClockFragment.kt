package com.rober.photoframe.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import com.rober.photoframe.R

class ClockFragment : Fragment() {

    private lateinit var btnBackToPhotos: Button
    private val hideButtonHandler = Handler(Looper.getMainLooper())
    private val hideButtonRunnable = Runnable { btnBackToPhotos.visibility = View.GONE }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_clock, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        btnBackToPhotos = view.findViewById(R.id.btnBackToPhotos)

        // Show button on tap anywhere
        view.setOnClickListener {
            toggleButton()
        }

        btnBackToPhotos.setOnClickListener {
            // Switch back to photo mode
            (activity as? com.rober.photoframe.MainActivity)?.switchToPhotoMode()
        }
    }

    private fun toggleButton() {
        if (btnBackToPhotos.visibility == View.VISIBLE) {
            btnBackToPhotos.visibility = View.GONE
            hideButtonHandler.removeCallbacks(hideButtonRunnable)
        } else {
            showButton()
        }
    }

    private fun showButton() {
        btnBackToPhotos.visibility = View.VISIBLE
        hideButtonHandler.removeCallbacks(hideButtonRunnable)
        hideButtonHandler.postDelayed(hideButtonRunnable, 3000)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        hideButtonHandler.removeCallbacks(hideButtonRunnable)
    }
}
