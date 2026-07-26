package com.rober.photoframe.ui

import android.view.View
import androidx.viewpager2.widget.ViewPager2
import com.rober.photoframe.settings.TransitionEffect
import kotlin.math.abs

/**
 * Page transitions for the slideshow.
 *
 * The `transitionEffect` preference has existed since the first version of the app and was
 * read by nothing — the setting was stored and silently ignored. These are the
 * implementations behind it.
 *
 * All three avoid allocating during the transform callback, which runs on every frame of
 * every transition. On a single-core tablet, allocation here causes visible stutter.
 */
object SlideTransformers {
    fun forEffect(effect: TransitionEffect): ViewPager2.PageTransformer =
        when (effect) {
            TransitionEffect.FADE -> Fade
            TransitionEffect.SLIDE -> Slide
            TransitionEffect.ZOOM -> Zoom
        }

    /**
     * Cross-fade. The page is held in place and only its alpha changes, which is the
     * cheapest of the three — no layer re-rasterisation.
     */
    private object Fade : ViewPager2.PageTransformer {
        override fun transformPage(
            page: View,
            position: Float,
        ) {
            page.translationX = -position * page.width
            page.alpha =
                when {
                    position <= -1f || position >= 1f -> 0f
                    else -> 1f - abs(position)
                }
        }
    }

    /** The platform default: pages slide horizontally with no extra effect. */
    private object Slide : ViewPager2.PageTransformer {
        override fun transformPage(
            page: View,
            position: Float,
        ) {
            page.alpha = 1f
            page.translationX = 0f
            page.scaleX = 1f
            page.scaleY = 1f
        }
    }

    /** Outgoing page shrinks and fades while the incoming page grows into place. */
    private object Zoom : ViewPager2.PageTransformer {
        private const val MIN_SCALE = 0.80f
        private const val MIN_ALPHA = 0.30f

        override fun transformPage(
            page: View,
            position: Float,
        ) {
            when {
                position <= -1f || position >= 1f -> {
                    page.alpha = 0f
                }
                else -> {
                    val scale = MIN_SCALE.coerceAtLeast(1f - abs(position))
                    page.scaleX = scale
                    page.scaleY = scale
                    page.translationX = -position * page.width
                    page.alpha = MIN_ALPHA + (scale - MIN_SCALE) / (1f - MIN_SCALE) *
                        (1f - MIN_ALPHA)
                }
            }
        }
    }
}
