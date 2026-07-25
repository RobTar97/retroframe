package com.rober.photoframe.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.viewpager2.widget.ViewPager2
import com.rober.photoframe.R
import com.rober.photoframe.settings.PhotoframePreferences
import com.rober.photoframe.settings.SettingsDialogFragment

class SlideshowFragment : Fragment() {

    private val viewModel: SlideshowViewModel by viewModels()
    private lateinit var viewPager: ViewPager2
    private lateinit var adapter: SlideshowAdapter
    private lateinit var controlsOverlay: LinearLayout
    private lateinit var btnPlayPause: ImageButton
    private lateinit var btnFavorite: ImageButton
    private var currentItemUri: String? = null
    
    private val hideControlsHandler = Handler(Looper.getMainLooper())
    private val hideControlsRunnable = Runnable { controlsOverlay.visibility = View.GONE }

    private val openDocumentTreeLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            requireContext().contentResolver.takePersistableUriPermission(it, takeFlags)
            
            PhotoframePreferences.galleryUriString = it.toString()
            viewModel.refreshMedia()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_slideshow, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewPager = view.findViewById(R.id.viewPager)
        controlsOverlay = view.findViewById(R.id.controlsOverlay)
        btnFavorite = view.findViewById(R.id.btnFavorite)
        btnPlayPause = view.findViewById(R.id.btnPlayPause)
        
        setupAdapter()
        setupControls(view)
        observeViewModel()
        
        // Initial check for gallery
        if (PhotoframePreferences.galleryUriString == null) {
            openDocumentTreeLauncher.launch(null)
        }
    }

    private fun setupAdapter() {
        adapter = SlideshowAdapter(
            requireContext(),
            onVideoEnded = {
                // On video ended, move to next
                val current = viewPager.currentItem
                val count = adapter.itemCount
                if (count > 0) {
                    viewPager.post {
                        viewPager.currentItem = (current + 1) % count
                    }
                }
            },
            onItemClicked = {
                toggleControls()
            }
        )
        viewPager.adapter = adapter
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                
                // Handle Slideshow Timer based on media type
                val items = viewModel.mediaItems.value
                val item = items?.getOrNull(position)
                
                // Update current item URI and favorite button state
                currentItemUri = item?.uri?.toString()
                updateFavoriteButton()
                
                if (item?.type == com.rober.photoframe.model.MediaType.VIDEO) {
                    // Stop timer for video, let onVideoEnded handle it
                    viewModel.stopSlideshow()
                    
                    // Start video playback NOW that it's visible
                    startVideoAtPosition(position)
                } else {
                    // Start/Restart timer for images
                    viewModel.startSlideshow(restart = true)
                }
                
                // Update position in ViewModel
                viewModel.updateCurrentPosition(position)
            }
            
            override fun onPageScrollStateChanged(state: Int) {
                super.onPageScrollStateChanged(state)
                // Pause all videos when user starts swiping
                if (state == ViewPager2.SCROLL_STATE_DRAGGING) {
                    pauseAllVideos()
                }
            }
        })
    }

    private fun setupControls(view: View) {
        // Toggle controls on tap
        val gestureDetector = android.view.GestureDetector(requireContext(), object : android.view.GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: android.view.MotionEvent): Boolean {
                toggleControls()
                return true
            }
        })

        viewPager.getChildAt(0).setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            false // Let ViewPager handle the touch for swiping
        }
        
        view.findViewById<View>(R.id.topOverlay).setOnClickListener { toggleControls() }
        
        view.findViewById<ImageButton>(R.id.btnSettings).setOnClickListener {
            showSettings()
            showControls() // Keep controls visible
        }
        
        view.findViewById<ImageButton>(R.id.btnClock).setOnClickListener {
            (activity as? com.rober.photoframe.MainActivity)?.switchToClockMode()
        }
        
        view.findViewById<ImageButton>(R.id.btnPrev).setOnClickListener {
            val current = viewPager.currentItem
            if (current > 0) viewPager.currentItem = current - 1
            showControls()
        }
        
        view.findViewById<ImageButton>(R.id.btnNext).setOnClickListener {
            val current = viewPager.currentItem
            val count = adapter.itemCount
            if (count > 0) viewPager.currentItem = (current + 1) % count
            showControls()
        }
        
        btnPlayPause.setOnClickListener {
            viewModel.toggleSlideshow()
            showControls()
        }
        
        btnFavorite.setOnClickListener {
            toggleFavorite()
            showControls()
        }
        
        view.findViewById<ImageButton>(R.id.btnRefresh).setOnClickListener {
            viewModel.refreshMedia()
            showControls()
        }
    }

    private fun toggleControls() {
        if (controlsOverlay.visibility == View.VISIBLE) {
            controlsOverlay.visibility = View.GONE
            hideControlsHandler.removeCallbacks(hideControlsRunnable)
        } else {
            showControls()
        }
    }

    private fun showControls() {
        controlsOverlay.visibility = View.VISIBLE
        hideControlsHandler.removeCallbacks(hideControlsRunnable)
        hideControlsHandler.postDelayed(hideControlsRunnable, 3000)
    }

    private fun observeViewModel() {
        viewModel.mediaItems.observe(viewLifecycleOwner) { items ->
            adapter.submitList(items)
        }

        viewModel.currentPosition.observe(viewLifecycleOwner) { position ->
            if (position < adapter.itemCount) {
                viewPager.currentItem = position
            }
        }

        viewModel.isSlideshowRunning.observe(viewLifecycleOwner) { isRunning ->
            btnPlayPause.setImageResource(
                if (isRunning) android.R.drawable.ic_media_pause 
                else android.R.drawable.ic_media_play
            )
        }
    }

    private fun showSettings() {
        val dialog = SettingsDialogFragment()
        dialog.onSettingsChanged = {
            viewModel.refreshMedia()
            viewModel.startSlideshow() 
        }
        dialog.onChangeFolderRequested = {
            openDocumentTreeLauncher.launch(null)
        }
        dialog.show(parentFragmentManager, "settings")
    }
    
    private fun toggleFavorite() {
        val uri = currentItemUri ?: return
        
        val isFavorited = com.rober.photoframe.data.FavoritesManager.toggleFavorite(uri)
        
        // Animate the button
        btnFavorite.animate()
            .scaleX(1.3f)
            .scaleY(1.3f)
            .setDuration(100)
            .withEndAction {
                btnFavorite.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(100)
                    .start()
            }
            .start()
        
        // Update icon
        updateFavoriteButton()
        
        // Show feedback
        val message = if (isFavorited) "Added to favorites ❤️" else "Removed from favorites"
        android.widget.Toast.makeText(requireContext(), message, android.widget.Toast.LENGTH_SHORT).show()
        
        // Reload media to apply new weighting
        viewModel.refreshMedia()
    }
    
    private fun updateFavoriteButton() {
        val uri = currentItemUri ?: return
        val isFavorited = com.rober.photoframe.data.FavoritesManager.isFavorite(uri)
        
        btnFavorite.setImageResource(
            if (isFavorited) R.drawable.ic_heart_filled
            else R.drawable.ic_heart_outline
        )
    }
    
    private fun startVideoAtPosition(position: Int) {
        // Small delay to ensure ViewPager transition is complete
        viewPager.postDelayed({
            try {
                val recyclerView = viewPager.getChildAt(0) as? androidx.recyclerview.widget.RecyclerView
                val viewHolder = recyclerView?.findViewHolderForAdapterPosition(position) as? SlideshowAdapter.SlideshowViewHolder
                viewHolder?.startPlayback()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, 100)
    }
    
    private fun pauseAllVideos() {
        try {
            val recyclerView = viewPager.getChildAt(0) as? androidx.recyclerview.widget.RecyclerView
            for (i in 0 until (recyclerView?.childCount ?: 0)) {
                val viewHolder = recyclerView?.getChildAt(i)?.let { 
                    recyclerView.getChildViewHolder(it) as? SlideshowAdapter.SlideshowViewHolder
                }
                viewHolder?.pausePlayback()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        hideControlsHandler.removeCallbacks(hideControlsRunnable)
    }
}
