package com.rober.photoframe.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.rober.photoframe.MainActivity
import com.rober.photoframe.R
import com.rober.photoframe.model.MediaItem
import com.rober.photoframe.settings.PhotoframePreferences
import com.rober.photoframe.settings.SettingsDialogFragment
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
class SlideshowFragment :
    Fragment(R.layout.fragment_slideshow),
    MainActivity.ScreenTapListener {

    private val viewModel: SlideshowViewModel by viewModels()

    private lateinit var viewPager: ViewPager2
    private lateinit var adapter: SlideshowAdapter
    private lateinit var controlsOverlay: View
    private lateinit var emptyState: TextView
    private lateinit var btnSelectFolder: View
    private lateinit var topOverlay: View
    private lateinit var btnPlayPause: ImageButton
    private lateinit var btnFavorite: ImageButton

    private lateinit var player: SharedPlayer

    private var currentItem: MediaItem? = null

    private val hideControls = Runnable { setControlsVisible(false) }

    private val folderPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        uri ?: return@registerForActivityResult

        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        try {
            requireContext().contentResolver.takePersistableUriPermission(uri, flags)
        } catch (e: SecurityException) {
            Toast.makeText(requireContext(), R.string.error_folder_permission, Toast.LENGTH_LONG)
                .show()
            return@registerForActivityResult
        }

        releasePreviousFolderPermission(uri.toString())
        PhotoframePreferences.galleryUriString = uri.toString()
        viewModel.onSettingsChanged(folderChanged = true)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewPager = view.findViewById(R.id.viewPager)
        controlsOverlay = view.findViewById(R.id.controlsOverlay)
        emptyState = view.findViewById(R.id.emptyState)
        btnSelectFolder = view.findViewById(R.id.btnSelectFolder)
        topOverlay = view.findViewById(R.id.topOverlay)
        btnPlayPause = view.findViewById(R.id.btnPlayPause)
        btnFavorite = view.findViewById(R.id.btnFavorite)

        player = SharedPlayer(requireContext().applicationContext)
        player.setOnVideoEnded { advance() }

        setUpPager()
        setUpControls(view)
        observeViewModel()

        if (PhotoframePreferences.galleryUriString == null) {
            startFolderSelection()
        }
    }

    /**
     * First run gets an explanation; afterwards the picker opens directly.
     */
    private fun startFolderSelection() {
        if (PhotoframePreferences.hasSeenWelcome) {
            openFolderPicker()
            return
        }
        if (parentFragmentManager.findFragmentByTag(WelcomeDialogFragment.TAG) != null) return

        PhotoframePreferences.hasSeenWelcome = true
        WelcomeDialogFragment().apply {
            onContinue = { openFolderPicker() }
        }.show(parentFragmentManager, WelcomeDialogFragment.TAG)
    }

    /**
     * Opens the system picker already inside a folder Android is willing to grant.
     *
     * Launching with no starting point drops the user at the top of storage, which Android 11
     * and newer refuse outright — as does Downloads. Starting in Pictures means the common
     * case needs one tap and never shows a refusal. Devices below API 26 ignore the hint and
     * behave as before, which is fine because they have no such restriction.
     */
    private fun openFolderPicker() {
        val start = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching {
                DocumentsContract.buildDocumentUri(
                    EXTERNAL_STORAGE_AUTHORITY,
                    "primary:$PREFERRED_START_FOLDER",
                )
            }.getOrNull()
        } else {
            null
        }
        folderPicker.launch(start)
    }

    // ------------------------------------------------------------------ pager

    private fun setUpPager() {
        adapter = SlideshowAdapter(requireContext())

        viewPager.adapter = adapter
        viewPager.setPageTransformer(
            SlideTransformers.forEffect(PhotoframePreferences.transitionEffect),
        )

        // One page either side is enough to make an advance feel instant, and is the most a
        // 1 GB device should be asked to hold decoded.
        viewPager.offscreenPageLimit = 1

        val recycler = viewPager.getChildAt(0) as? RecyclerView

        // Disable the over-scroll glow: it allocates a bitmap and never looks right against
        // a photo bleeding to the screen edge.
        recycler?.overScrollMode = View.OVER_SCROLL_NEVER

        // Tap-to-reveal is driven by MainActivity.dispatchTouchEvent, which is the only layer
        // PhotoView cannot cut out of the touch stream. See onScreenTapped below.

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                onSlideSelected(position)
            }

            override fun onPageScrollStateChanged(state: Int) {
                if (state == ViewPager2.SCROLL_STATE_DRAGGING) {
                    // The user is taking over; stop any video mid-swipe.
                    player.pause()
                }
            }
        })
    }

    private fun onSlideSelected(position: Int) {
        val item = adapter.itemAt(position)
        currentItem = item
        updateFavoriteIcon()

        if (item == null) return

        if (item.isVideo) {
            startVideo(position)
        } else {
            player.detach()
            preloadNeighbour(position)
        }

        viewModel.onSlideShown(item)
    }

    private fun startVideo(position: Int) {
        // The ViewHolder may not exist yet if the page was just created; post so the pager
        // has finished laying out.
        viewPager.post {
            if (!isAdded) return@post
            val recycler = viewPager.getChildAt(0) as? RecyclerView ?: return@post
            val holder = recycler.findViewHolderForAdapterPosition(position)
                as? SlideshowAdapter.SlideViewHolder ?: return@post
            val item = adapter.itemAt(position) ?: return@post
            player.playOn(holder.playerView, item.uri)
        }
    }

    /** Warms the cache for the next photo so the advance does not stall on slow storage. */
    private fun preloadNeighbour(position: Int) {
        val next = adapter.itemAt(position + 1) ?: adapter.itemAt(0) ?: return
        if (!next.isVideo) {
            ImageLoader.preload(requireContext(), next.uri)
        }
    }

    private fun advance() {
        val count = adapter.itemCount
        if (count == 0) return
        viewPager.currentItem = (viewPager.currentItem + 1) % count
    }

    // --------------------------------------------------------------- controls

    private fun setUpControls(view: View) {
        view.findViewById<ImageButton>(R.id.btnSettings).setOnClickListener {
            showSettings()
            showControls()
        }

        view.findViewById<ImageButton>(R.id.btnClock).setOnClickListener {
            (activity as? MainActivity)?.switchToClockMode()
        }

        view.findViewById<ImageButton>(R.id.btnPrev).setOnClickListener {
            val count = adapter.itemCount
            if (count > 0) {
                viewPager.currentItem = (viewPager.currentItem - 1 + count) % count
                viewModel.resetTimer()
            }
            showControls()
        }

        view.findViewById<ImageButton>(R.id.btnNext).setOnClickListener {
            advance()
            viewModel.resetTimer()
            showControls()
        }

        btnPlayPause.setOnClickListener {
            viewModel.togglePlayPause()
            showControls()
        }

        btnFavorite.setOnClickListener {
            toggleFavorite()
            showControls()
        }

        view.findViewById<ImageButton>(R.id.btnRefresh).setOnClickListener {
            viewModel.reload()
            showControls()
        }

        btnSelectFolder.setOnClickListener { startFolderSelection() }
    }

    /**
     * A bare tap anywhere on the screen shows or hides the controls — except when it lands on
     * the chrome itself, where the buttons have already handled it and toggling as well would
     * make every button press also dismiss the bar it lives on.
     */
    override fun onScreenTapped(rawX: Int, rawY: Int) {
        if (!isAdded) return
        if (controlsOverlay.visibility == View.VISIBLE && hits(controlsOverlay, rawX, rawY)) return
        if (hits(topOverlay, rawX, rawY)) return
        if (btnSelectFolder.visibility == View.VISIBLE && hits(btnSelectFolder, rawX, rawY)) return
        toggleControls()
    }

    private fun hits(target: View, rawX: Int, rawY: Int): Boolean {
        val xy = IntArray(2)
        target.getLocationOnScreen(xy)
        return rawX >= xy[0] && rawX <= xy[0] + target.width &&
            rawY >= xy[1] && rawY <= xy[1] + target.height
    }

    private fun toggleControls() = setControlsVisible(controlsOverlay.visibility != View.VISIBLE)

    private fun showControls() = setControlsVisible(true)

    private fun setControlsVisible(visible: Boolean) {
        controlsOverlay.removeCallbacks(hideControls)
        controlsOverlay.visibility = if (visible) View.VISIBLE else View.GONE
        if (visible) {
            controlsOverlay.postDelayed(hideControls, CONTROLS_TIMEOUT_MS)
        }
    }

    private fun toggleFavorite() {
        val item = currentItem ?: return
        val nowFavorite = viewModel.toggleFavorite(item)

        btnFavorite.animate()
            .scaleX(1.3f).scaleY(1.3f)
            .setDuration(100)
            .withEndAction {
                btnFavorite.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
            }
            .start()

        updateFavoriteIcon()

        Toast.makeText(
            requireContext(),
            if (nowFavorite) R.string.favorite_added else R.string.favorite_removed,
            Toast.LENGTH_SHORT,
        ).show()
        // Deliberately no reload here — see SlideshowViewModel.toggleFavorite.
    }

    private fun updateFavoriteIcon() {
        val item = currentItem
        val favorite = item != null && viewModel.isFavorite(item)
        btnFavorite.setImageResource(
            if (favorite) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline,
        )
    }

    private fun showSettings() {
        SettingsDialogFragment().apply {
            onSettingsSaved = { viewModel.onSettingsChanged(folderChanged = false) }
            onChangeFolderRequested = { openFolderPicker() }
        }.show(parentFragmentManager, "settings")
    }

    // ------------------------------------------------------------ view model

    private fun observeViewModel() {
        viewModel.state.observe(viewLifecycleOwner) { state ->
            when (state) {
                is SlideshowState.Ready -> {
                    setEmptyVisible(false)
                    viewPager.visibility = View.VISIBLE
                    adapter.submitList(state.playlist)
                    // A fresh playlist means position 0 is now a different photo.
                    viewPager.setCurrentItem(0, false)
                    onSlideSelected(0)
                }
                SlideshowState.FolderEmpty -> showEmpty(R.string.empty_folder)
                SlideshowState.NoFolderSelected -> showEmpty(R.string.empty_no_folder)
                SlideshowState.Loading -> Unit
            }
        }

        viewModel.isPlaying.observe(viewLifecycleOwner) { playing ->
            btnPlayPause.setImageResource(
                if (playing) android.R.drawable.ic_media_pause
                else android.R.drawable.ic_media_play,
            )
            if (!playing) player.pause()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.advanceRequests.collect { advance() }
            }
        }
    }

    private fun showEmpty(messageRes: Int) {
        adapter.submitList(emptyList())
        viewPager.visibility = View.GONE
        emptyState.setText(messageRes)
        setEmptyVisible(true)
        showControls()
    }

    /**
     * The message and its button are one unit. They were previously toggled separately, and
     * the button was never hidden — so "Choose folder" sat on top of every photo for the
     * entire life of the slideshow.
     */
    private fun setEmptyVisible(visible: Boolean) {
        val visibility = if (visible) View.VISIBLE else View.GONE
        emptyState.visibility = visibility
        btnSelectFolder.visibility = visibility
    }

    // ------------------------------------------------------------- lifecycle

    override fun onStart() {
        super.onStart()
        // Watching the folder is pointless while the slideshow is off screen.
        viewModel.startWatching()
    }

    override fun onStop() {
        super.onStop()
        viewModel.stopWatching()
        player.pause()
    }

    override fun onDestroyView() {
        controlsOverlay.removeCallbacks(hideControls)
        player.release()
        viewPager.adapter = null
        super.onDestroyView()
    }

    /**
     * SAF grants are a limited, persistent, per-app resource. Without releasing the old one,
     * a user who re-picks their folder a few dozen times over the years eventually exhausts
     * the quota and folder selection starts failing for no visible reason.
     */
    private fun releasePreviousFolderPermission(newUri: String) {
        val previous = PhotoframePreferences.galleryUriString ?: return
        if (previous == newUri) return
        try {
            requireContext().contentResolver.releasePersistableUriPermission(
                android.net.Uri.parse(previous),
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        } catch (e: SecurityException) {
            // Already gone; nothing to release.
        }
    }

    private companion object {
        const val CONTROLS_TIMEOUT_MS = 3_000L

        /** Android's own provider for the built-in storage volume. */
        const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"

        /**
         * Where the picker opens. Pictures exists on every Android device, is never one of
         * the folders the system refuses, and is where a PC's file browser puts photos.
         */
        const val PREFERRED_START_FOLDER = "Pictures"
    }
}
