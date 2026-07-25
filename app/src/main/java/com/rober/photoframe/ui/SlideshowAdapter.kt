package com.rober.photoframe.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.RecyclerView
import com.github.chrisbanes.photoview.PhotoView
import com.rober.photoframe.R
import com.rober.photoframe.model.MediaItem

/**
 * One page per slide.
 *
 * Video pages render nothing until they become the active page, at which point the fragment
 * hands them the single [SharedPlayer]. This is what allows one decoder to serve the whole
 * slideshow.
 */
@OptIn(UnstableApi::class)
class SlideshowAdapter(
    private val context: Context,
    private val onItemClicked: () -> Unit,
) : RecyclerView.Adapter<SlideshowAdapter.SlideViewHolder>() {

    private var items: List<MediaItem> = emptyList()

    /**
     * DiffUtil is deliberately not used here. The playlist may legitimately contain the same
     * photo more than once (favourite weighting), so there is no stable per-position identity
     * for DiffUtil to match on. A playlist change also means the entire ordering changed, so
     * a full rebind is the honest representation — and it now happens only on a real folder
     * or settings change rather than on every heart tap.
     */
    @Suppress("NotifyDataSetChanged")
    fun submitList(newItems: List<MediaItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    fun itemAt(position: Int): MediaItem? = items.getOrNull(position)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SlideViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_slideshow, parent, false)
        return SlideViewHolder(view)
    }

    override fun onBindViewHolder(holder: SlideViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    override fun onViewRecycled(holder: SlideViewHolder) {
        super.onViewRecycled(holder)
        holder.recycle()
    }

    inner class SlideViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        private val imageView: PhotoView = itemView.findViewById(R.id.imageView)
        val playerView: PlayerView = itemView.findViewById(R.id.playerView)

        fun bind(item: MediaItem) {
            itemView.setOnClickListener { onItemClicked() }

            if (item.isVideo) {
                imageView.visibility = View.GONE
                ImageLoader.clear(context, imageView)

                playerView.visibility = View.VISIBLE
                playerView.useController = false
                playerView.setOnClickListener { onItemClicked() }
                // Playback starts only once this page becomes active — see
                // SlideshowFragment.onPageSelected.
            } else {
                playerView.visibility = View.GONE
                imageView.visibility = View.VISIBLE

                // PhotoView consumes touch events for pinch-zoom, so a plain click listener
                // never fires; these are the hooks it exposes for taps that are not gestures.
                imageView.setOnPhotoTapListener { _, _, _ -> onItemClicked() }
                imageView.setOnViewTapListener { _, _, _ -> onItemClicked() }

                ImageLoader.load(context, item.uri, imageView)
            }
        }

        fun recycle() {
            // Cancel any in-flight decode so a fast swipe does not leave work queued for a
            // page that is already gone.
            ImageLoader.clear(context, imageView)
            imageView.setImageDrawable(null)
            playerView.player = null
        }
    }
}
