package com.hemendra.depthwallpaper.wallpaper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import com.hemendra.depthwallpaper.data.WallpaperConfig

/**
 * Composites the three depth layers onto a [Canvas]:
 *
 *   1. background photo   (slides most with tilt — the "far" plane)
 *   2. clock              (fixed — the "middle" plane)
 *   3. subject cut-out    (slides opposite the background — the "near" plane)
 *
 * Because the subject is painted last, it overlaps the clock exactly where the
 * subject's pixels are, producing the iOS-style depth effect. The opposing
 * parallax between background and subject exaggerates the separation.
 *
 * Layers are pre-scaled once (via [prepare]) with a small overscan margin so
 * there is slack to slide into without exposing the canvas edges.
 */
class DepthRenderer {

    private val clock = ClockRenderer()
    private val layerPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

    /** Layers scaled to cover a specific surface size, ready to blit each frame. */
    class Prepared(
        val background: Bitmap,
        val foreground: Bitmap,
        val baseLeft: Float,
        val baseTop: Float,
        val maxSlidePx: Float,
    )

    /**
     * Scale [background] and [foreground] (assumed identical source dimensions,
     * as produced by segmentation) to center-crop-cover [width] x [height] plus
     * an overscan margin. Returns null if inputs are unusable.
     */
    fun prepare(background: Bitmap, foreground: Bitmap, width: Int, height: Int): Prepared? {
        if (width <= 0 || height <= 0) return null
        val srcW = background.width.toFloat()
        val srcH = background.height.toFloat()
        if (srcW <= 0f || srcH <= 0f) return null

        val overscan = 1f + 2f * MAX_SLIDE_FRAC
        val scale = maxOf(width * overscan / srcW, height * overscan / srcH)
        val scaledW = (srcW * scale).toInt().coerceAtLeast(1)
        val scaledH = (srcH * scale).toInt().coerceAtLeast(1)

        val bg = Bitmap.createScaledBitmap(background, scaledW, scaledH, true)
        // Foreground matches background dimensions; scale identically so the two
        // layers stay pixel-aligned.
        val fg = Bitmap.createScaledBitmap(foreground, scaledW, scaledH, true)

        return Prepared(
            background = bg,
            foreground = fg,
            baseLeft = (width - scaledW) / 2f,
            baseTop = (height - scaledH) / 2f,
            maxSlidePx = width * MAX_SLIDE_FRAC,
        )
    }

    fun draw(
        canvas: Canvas,
        prepared: Prepared,
        config: WallpaperConfig,
        offsetX: Float,
        offsetY: Float,
    ) {
        val slide = prepared.maxSlidePx
        val bgAmp = config.parallaxStrength
        // Subject counter-moves; scaled by both parallax and the "pop" control.
        val fgAmp = -config.parallaxStrength * config.subjectPop

        // Background — furthest, moves with tilt.
        canvas.drawBitmap(
            prepared.background,
            prepared.baseLeft - offsetX * slide * bgAmp,
            prepared.baseTop - offsetY * slide * bgAmp,
            layerPaint,
        )

        // Clock — anchored near the top, like a lock screen.
        clock.draw(
            canvas = canvas,
            centerX = canvas.width / 2f,
            topY = canvas.height * 0.16f,
            color = config.clockColor,
            use24Hour = config.use24Hour,
            showDate = config.showDate,
        )

        // Subject cut-out — nearest, moves opposite the background, over the clock.
        canvas.drawBitmap(
            prepared.foreground,
            prepared.baseLeft - offsetX * slide * fgAmp,
            prepared.baseTop - offsetY * slide * fgAmp,
            layerPaint,
        )
    }

    private companion object {
        /** Overscan / max slide as a fraction of screen width per side. */
        const val MAX_SLIDE_FRAC = 0.08f
    }
}
