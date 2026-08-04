package com.rober.photoframe.ui

import android.view.View

/**
 * Slowly walks static on-screen text around a small box so it cannot mark the panel.
 *
 * ## Why a photo frame needs this and other apps do not
 *
 * Ordinary apps are looked at for minutes. A photo frame is left on a shelf, showing the same
 * clock in the same place, for years. On the OLED panels in tablets like the Galaxy Tab S2 —
 * exactly the sort of device this project is trying to rescue — that is enough to permanently
 * ghost the digits into the display. LCDs are far more tolerant but not immune; long-term
 * image retention on cheap IPS panels is a real, documented effect.
 *
 * ## What moves, and what does not
 *
 * Only the bright text moves. The slideshow's translucent top bar stays where it is: it spans
 * the full width, so shifting it would expose a black gap along one edge, and the photo
 * underneath changes every few seconds anyway, which spreads the wear on its own.
 *
 * ## Why the motion looks like this
 *
 * The offsets trace a slow, closed loop rather than jumping randomly. A random walk drifts
 * off-centre and, worse, is visible — a clock that hops is a clock the owner will notice. One
 * step per [STEP_INTERVAL_MS] over [MAX_OFFSET_DP] is roughly a pixel a minute: invisible in
 * the moment, several hundred pixels of travel over a week.
 */
class BurnInGuard(private val views: List<View>) {
    private var step = 0
    private var running = false

    private val tick =
        object : Runnable {
            override fun run() {
                if (!running) return
                step = (step + 1) % CYCLE_STEPS
                apply()
                views.firstOrNull()?.postDelayed(this, STEP_INTERVAL_MS)
            }
        }

    fun start() {
        if (running || views.isEmpty()) return
        running = true
        apply()
        views.first().postDelayed(tick, STEP_INTERVAL_MS)
    }

    fun stop() {
        running = false
        // Plain for loops, not Iterable.forEach: on a List that resolves to the Java 8 member
        // function, which does not exist on Android 5.1 and would crash the frame at runtime.
        for (view in views) {
            view.removeCallbacks(tick)
            // Leave nothing behind: a view recycled into another screen must not keep an
            // offset it was given here.
            view.translationX = 0f
            view.translationY = 0f
        }
    }

    private fun apply() {
        val view = views.firstOrNull() ?: return
        val density = view.resources.displayMetrics.density
        val radius = MAX_OFFSET_DP * density

        val angle = TWO_PI * step / CYCLE_STEPS
        val x = (radius * Math.cos(angle)).toFloat()
        // Half the vertical travel: on a landscape frame there is more room to move sideways,
        // and vertical motion of the clock is the easier of the two to notice.
        val y = (radius * Math.sin(angle) * 0.5).toFloat()

        for (target in views) {
            target.translationX = x
            target.translationY = y
        }
    }

    private companion object {
        /** One nudge a minute. Long enough to be invisible, short enough to keep moving. */
        const val STEP_INTERVAL_MS = 60_000L

        /** Steps in a full loop — one revolution every two hours. */
        const val CYCLE_STEPS = 120

        /** How far from centre the text is ever placed. */
        const val MAX_OFFSET_DP = 8f

        const val TWO_PI = 2 * Math.PI
    }
}
