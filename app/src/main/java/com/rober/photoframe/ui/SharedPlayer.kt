package com.rober.photoframe.ui

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.rober.photoframe.settings.PhotoframePreferences

/**
 * A single video player shared by the whole slideshow.
 *
 * The previous design built one `ExoPlayer` inside every ViewHolder. ViewPager2 keeps
 * offscreen pages alive, so up to three players — and therefore up to three hardware decoder
 * sessions — could exist at once. Old tablets typically have a single hardware video decoder;
 * the extra instances either fell back to software decoding or failed outright, and each one
 * held its own multi-megabyte buffers on a device with 1 GB of RAM.
 *
 * Only one video is ever visible, so only one player is ever needed.
 */
@OptIn(UnstableApi::class)
class SharedPlayer(private val context: Context) {
    private var player: ExoPlayer? = null
    private var attachedView: PlayerView? = null
    private var onEnded: (() -> Unit)? = null

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_ENDED) onEnded?.invoke()
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            // A codec this device lacks must not stop the slideshow — skip to the next item.
            onEnded?.invoke()
        }
    }

    fun setOnVideoEnded(callback: () -> Unit) {
        onEnded = callback
    }

    private fun obtain(): ExoPlayer = player ?: ExoPlayer.Builder(context)
        .setLoadControl(
            // Small buffers. The default targets streaming over a network; these files are
            // local, and buffer memory is scarce on the devices this app runs on.
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    /* minBufferMs = */ 2_000,
                    /* maxBufferMs = */ 10_000,
                    /* bufferForPlaybackMs = */ 500,
                    /* bufferForPlaybackAfterRebufferMs = */ 1_000,
                )
                .build(),
        )
        .build()
        .also {
            it.repeatMode = Player.REPEAT_MODE_OFF
            it.addListener(listener)
            player = it
        }

    /** Moves the player onto [view] and starts [uri]. Any previous attachment is released. */
    fun playOn(view: PlayerView, uri: Uri) {
        val exo = obtain()

        if (attachedView !== view) {
            attachedView?.player = null
            view.player = exo
            attachedView = view
        }

        exo.volume = if (PhotoframePreferences.videoSoundEnabled) 1f else 0f
        exo.setMediaItem(MediaItem.fromUri(uri))
        exo.prepare()
        exo.playWhenReady = true
    }

    fun pause() {
        player?.playWhenReady = false
    }

    /** Detaches from the current view and stops playback, keeping the player for reuse. */
    fun detach() {
        player?.let {
            it.playWhenReady = false
            it.stop()
            it.clearMediaItems()
        }
        attachedView?.player = null
        attachedView = null
    }

    /** Fully releases the decoder. Call when the slideshow leaves the screen. */
    fun release() {
        detach()
        player?.removeListener(listener)
        player?.release()
        player = null
    }
}
