package com.hemendra.depthwallpaper.wallpaper

import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.os.Handler
import android.os.HandlerThread
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import com.hemendra.depthwallpaper.data.WallpaperConfig
import com.hemendra.depthwallpaper.data.WallpaperRepository

/**
 * Live wallpaper that renders the depth composition (background ▸ clock ▸
 * subject cut-out) with gyroscopic parallax.
 *
 * Rendering runs on a dedicated [HandlerThread]; draws are requested by the
 * parallax sensor (coalesced) and by a 1 Hz tick that keeps the clock current.
 */
class DepthWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = DepthEngine()

    private inner class DepthEngine : Engine(),
        SharedPreferences.OnSharedPreferenceChangeListener {

        private val repository = WallpaperRepository.get(this@DepthWallpaperService)
        private val prefs = getSharedPreferences("depth_wallpaper_prefs", MODE_PRIVATE)

        private val renderer = DepthRenderer()
        private val parallax = ParallaxController(this@DepthWallpaperService) { requestRender() }

        private var renderThread: HandlerThread? = null
        private var handler: Handler? = null

        private var prepared: DepthRenderer.Prepared? = null
        private var loadedVersion = -1
        private var surfaceWidth = 0
        private var surfaceHeight = 0
        private var visible = false

        private val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val placeholderClock = ClockRenderer()

        private val frame = Runnable { drawFrame() }
        private val tick = object : Runnable {
            override fun run() {
                requestRender()
                if (visible) handler?.postDelayed(this, CLOCK_TICK_MS)
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            HandlerThread("depth-render").also {
                it.start()
                renderThread = it
                handler = Handler(it.looper)
            }
            prefs.registerOnSharedPreferenceChangeListener(this)
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            surfaceWidth = width
            surfaceHeight = height
            handler?.post {
                reprepare()
                drawFrame()
            }
        }

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            if (visible) {
                parallax.start()
                handler?.post {
                    reloadIfStale()
                    drawFrame()
                }
                handler?.postDelayed(tick, CLOCK_TICK_MS)
            } else {
                parallax.stop()
                handler?.removeCallbacks(tick)
            }
        }

        override fun onSharedPreferenceChanged(sp: SharedPreferences?, key: String?) {
            // Any settings change (clock colour, parallax, or a new image) — the
            // cheap path just redraws; a new image also reloads the layers.
            handler?.post {
                reloadIfStale()
                drawFrame()
            }
        }

        override fun onDestroy() {
            super.onDestroy()
            parallax.stop()
            prefs.unregisterOnSharedPreferenceChangeListener(this)
            handler?.removeCallbacksAndMessages(null)
            renderThread?.quitSafely()
            renderThread = null
            handler = null
            prepared = null
        }

        // --- rendering ----------------------------------------------------

        private fun requestRender() {
            val h = handler ?: return
            h.removeCallbacks(frame)
            h.post(frame)
        }

        private fun reloadIfStale() {
            if (repository.imageVersion() != loadedVersion) reprepare()
        }

        private fun reprepare() {
            val config = repository.currentConfig()
            if (!config.hasImage || surfaceWidth == 0 || surfaceHeight == 0) {
                prepared = null
                loadedVersion = repository.imageVersion()
                return
            }
            val background = repository.loadBackground()
            val foreground = repository.loadForeground()
            prepared = if (background != null && foreground != null) {
                renderer.prepare(background, foreground, surfaceWidth, surfaceHeight)
            } else {
                null
            }
            loadedVersion = repository.imageVersion()
        }

        private fun drawFrame() {
            val holder = surfaceHolder
            var canvas: Canvas? = null
            try {
                canvas = holder.lockCanvas() ?: return
                val config = repository.currentConfig()
                val ready = prepared
                if (ready != null) {
                    renderer.draw(canvas, ready, config, parallax.offsetX, parallax.offsetY)
                } else {
                    drawPlaceholder(canvas, config)
                }
            } finally {
                if (canvas != null) holder.unlockCanvasAndPost(canvas)
            }
        }

        /** Shown before a photo has been chosen: a gradient plus the clock. */
        private fun drawPlaceholder(canvas: Canvas, config: WallpaperConfig) {
            placeholderPaint.shader = LinearGradient(
                0f, 0f, 0f, canvas.height.toFloat(),
                Color.rgb(18, 22, 46), Color.rgb(5, 6, 14),
                Shader.TileMode.CLAMP,
            )
            canvas.drawRect(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat(), placeholderPaint)
            placeholderPaint.shader = null

            placeholderClock.draw(
                canvas = canvas,
                centerX = canvas.width / 2f,
                topY = canvas.height * 0.16f,
                color = config.clockColor,
                use24Hour = config.use24Hour,
                showDate = config.showDate,
            )
        }
    }

    private companion object {
        const val CLOCK_TICK_MS = 1_000L
    }
}
