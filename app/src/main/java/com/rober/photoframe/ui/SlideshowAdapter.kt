package com.rober.photoframe.ui

import android.content.Context
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.github.chrisbanes.photoview.PhotoView
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ui.StyledPlayerView
import com.rober.photoframe.R
import com.rober.photoframe.model.MediaType
import com.rober.photoframe.settings.PhotoframePreferences
import com.rober.photoframe.model.MediaItem as AppMediaItem

class SlideshowAdapter(
    private val context: Context,
    private val onVideoEnded: () -> Unit,
    private val onItemClicked: () -> Unit
) : RecyclerView.Adapter<SlideshowAdapter.SlideshowViewHolder>() {

    private var items: List<AppMediaItem> = emptyList()

    fun submitList(newItems: List<AppMediaItem>) {
        items = newItems
        notifyDataSetChanged()
    }
    
    // ... (onCreateViewHolder, etc.)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SlideshowViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_slideshow, parent, false)
        return SlideshowViewHolder(view)
    }

    override fun onBindViewHolder(holder: SlideshowViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    override fun onViewRecycled(holder: SlideshowViewHolder) {
        super.onViewRecycled(holder)
        holder.releasePlayer()
    }

    inner class SlideshowViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageView: PhotoView = itemView.findViewById(R.id.imageView)
        private val playerView: StyledPlayerView = itemView.findViewById(R.id.playerView)
        private var player: ExoPlayer? = null

        fun bind(item: AppMediaItem) {
            // Ensure clicks on the item view itself (background/margins) also trigger the listener
            itemView.setOnClickListener { onItemClicked() }
            
            if (item.type == MediaType.VIDEO) {
                imageView.visibility = View.GONE
                playerView.visibility = View.VISIBLE
                playerView.setOnClickListener { onItemClicked() }
                setupPlayer(item.uri)
            } else {
                playerView.visibility = View.GONE
                imageView.visibility = View.VISIBLE
                
                // PhotoView consumes touches, so we need its specific listener
                imageView.setOnPhotoTapListener { _, _, _ -> 
                    onItemClicked() 
                }
                // Also handle simple clicks if not zoomed
                imageView.setOnClickListener { onItemClicked() }
                
                // IMPORTANT: Ensure PhotoView doesn't block parent clicks if it doesn't handle them
                imageView.setOnViewTapListener { _, _, _ ->
                     onItemClicked()
                }

                Glide.with(context)
                    .load(item.uri)
                    .override(2048, 2048) // Limit max size to prevent OOM
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .into(imageView)
            }
        }

        private fun setupPlayer(uri: Uri) {
            if (player == null) {
                player = ExoPlayer.Builder(context).build().apply {
                    repeatMode = Player.REPEAT_MODE_OFF
                    volume = if (PhotoframePreferences.videoSoundEnabled) 1f else 0f
                    addListener(object : Player.Listener {
                        override fun onPlaybackStateChanged(playbackState: Int) {
                            if (playbackState == Player.STATE_ENDED) {
                                onVideoEnded()
                            }
                        }
                    })
                }
                playerView.player = player
            }
            
            player?.setMediaItem(MediaItem.fromUri(uri))
            player?.prepare()
            // Don't auto-play - wait for Fragment to call startPlayback()
            player?.playWhenReady = false
        }
        
        fun startPlayback() {
            player?.playWhenReady = true
        }
        
        fun pausePlayback() {
            player?.playWhenReady = false
        }

        fun releasePlayer() {
            player?.stop()
            player?.release()
            player = null
            playerView.player = null
        }
    }
}
