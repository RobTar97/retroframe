package com.rober.photoframe.ui

import android.app.ActivityManager
import android.content.Context
import android.net.Uri
import android.util.DisplayMetrics
import android.widget.ImageView
import androidx.core.content.getSystemService
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.DownsampleStrategy
import com.bumptech.glide.request.RequestOptions

/**
 * Image loading tuned for the hardware RetroFrame targets.
 *
 * The old code decoded every photo at up to 2048×2048. A typical old tablet panel is
 * 1280×800 — roughly four times fewer pixels — so three quarters of every decoded bitmap was
 * memory that could never be displayed, on the devices least able to spare it.
 *
 * Decode size is now derived from the actual display, and low-RAM devices additionally use
 * RGB_565, which halves bitmap memory. The banding that costs is invisible on the panels
 * these devices ship with.
 */
object ImageLoader {
    private var decodeWidth = 1280
    private var decodeHeight = 800
    private var lowRam = false
    private var initialised = false

    fun init(context: Context) {
        if (initialised) return
        initialised = true

        val metrics: DisplayMetrics = context.resources.displayMetrics
        // Long edge either way, so rotation does not force a re-decode at a larger size.
        val longEdge = maxOf(metrics.widthPixels, metrics.heightPixels)
        val shortEdge = minOf(metrics.widthPixels, metrics.heightPixels)
        decodeWidth = longEdge.coerceAtLeast(640)
        decodeHeight = shortEdge.coerceAtLeast(480)

        val activityManager = context.getSystemService<ActivityManager>()
        lowRam = activityManager?.isLowRamDevice == true ||
            (activityManager?.memoryClass ?: 0) <= 96
    }

    private fun options(): RequestOptions = RequestOptions()
        .override(decodeWidth, decodeHeight)
        // AT_MOST never upscales, so a small photo is not blown up into a large bitmap.
        .downsample(DownsampleStrategy.AT_MOST)
        .format(if (lowRam) DecodeFormat.PREFER_RGB_565 else DecodeFormat.PREFER_ARGB_8888)
        // The source folder can be on a removable card that disappears; cache the decoded
        // result so the slideshow survives a brief unmount.
        .diskCacheStrategy(DiskCacheStrategy.RESOURCE)

    fun load(context: Context, uri: Uri, into: ImageView) {
        Glide.with(context)
            .load(uri)
            .apply(options())
            // Page transitions are handled by the ViewPager transformer; a Glide crossfade
            // on top of that reads as a double animation.
            .dontAnimate()
            .into(into)
    }

    /**
     * Decodes the next photo into cache while the current one is displayed, so the advance
     * does not stall on slow eMMC storage.
     */
    fun preload(context: Context, uri: Uri) {
        Glide.with(context)
            .load(uri)
            .apply(options())
            .preload(decodeWidth, decodeHeight)
    }

    fun clear(context: Context, view: ImageView) {
        Glide.with(context).clear(view)
    }
}
