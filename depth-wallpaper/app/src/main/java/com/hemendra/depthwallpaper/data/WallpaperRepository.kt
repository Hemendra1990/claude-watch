package com.hemendra.depthwallpaper.data

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Single source of truth for the two rendered layers (background + segmented
 * foreground) and the [WallpaperConfig].
 *
 * Backed by [SharedPreferences] so both the Compose UI and the
 * [com.hemendra.depthwallpaper.wallpaper.DepthWallpaperService] read/write the
 * same state and can react to each other's changes live via
 * [OnSharedPreferenceChangeListener].
 *
 * Access through [get] — it is a process-wide singleton.
 */
class WallpaperRepository private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val backgroundFile = File(appContext.filesDir, "depth_background.png")
    private val foregroundFile = File(appContext.filesDir, "depth_foreground.png")

    private val _config = MutableStateFlow(readConfig())
    val config: StateFlow<WallpaperConfig> = _config.asStateFlow()

    private val listener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            _config.value = readConfig()
        }

    init {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    // --- Layers -----------------------------------------------------------

    /** Persist a freshly processed photo. [foreground] is the subject cut-out. */
    suspend fun saveLayers(background: Bitmap, foreground: Bitmap) =
        withContext(Dispatchers.IO) {
            writePng(background, backgroundFile)
            writePng(foreground, foregroundFile)
            // Bump the version so a running wallpaper engine knows to reload the
            // layers even though hasImage may already have been true.
            prefs.edit().putInt(KEY_IMAGE_VERSION, imageVersion() + 1).apply()
            updateConfig { it.copy(hasImage = true) }
        }

    /** Monotonic counter incremented every time new layers are saved. */
    fun imageVersion(): Int = prefs.getInt(KEY_IMAGE_VERSION, 0)

    /** Decoded background layer, or null if none has been saved yet. */
    fun loadBackground(): Bitmap? = decode(backgroundFile)

    /** Decoded foreground (subject) layer, or null if none has been saved. */
    fun loadForeground(): Bitmap? = decode(foregroundFile)

    // --- Config -----------------------------------------------------------

    /** Atomically mutate the config; persisted immediately. */
    fun updateConfig(transform: (WallpaperConfig) -> WallpaperConfig) {
        val next = transform(_config.value)
        prefs.edit().apply {
            putInt(KEY_CLOCK_COLOR, next.clockColor)
            putBoolean(KEY_SHOW_DATE, next.showDate)
            putBoolean(KEY_USE_24H, next.use24Hour)
            putFloat(KEY_PARALLAX, next.parallaxStrength)
            putFloat(KEY_POP, next.subjectPop)
            putBoolean(KEY_HAS_IMAGE, next.hasImage)
        }.apply()
        // Value is refreshed by the change listener, but set eagerly so callers
        // on this thread observe it immediately.
        _config.value = next
    }

    /** Current config snapshot for synchronous readers (the render thread). */
    fun currentConfig(): WallpaperConfig = readConfig()

    private fun readConfig(): WallpaperConfig = WallpaperConfig(
        clockColor = prefs.getInt(KEY_CLOCK_COLOR, 0xFFFFFFFF.toInt()),
        showDate = prefs.getBoolean(KEY_SHOW_DATE, true),
        use24Hour = prefs.getBoolean(KEY_USE_24H, false),
        parallaxStrength = prefs.getFloat(KEY_PARALLAX, 0.6f),
        subjectPop = prefs.getFloat(KEY_POP, 0.5f),
        hasImage = prefs.getBoolean(KEY_HAS_IMAGE, false),
    )

    private fun decode(file: File): Bitmap? =
        if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null

    private fun writePng(bitmap: Bitmap, file: File) {
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }

    companion object {
        private const val PREFS = "depth_wallpaper_prefs"
        private const val KEY_CLOCK_COLOR = "clock_color"
        private const val KEY_SHOW_DATE = "show_date"
        private const val KEY_USE_24H = "use_24h"
        private const val KEY_PARALLAX = "parallax"
        private const val KEY_POP = "pop"
        private const val KEY_HAS_IMAGE = "has_image"
        private const val KEY_IMAGE_VERSION = "image_version"

        @Volatile
        private var instance: WallpaperRepository? = null

        fun get(context: Context): WallpaperRepository =
            instance ?: synchronized(this) {
                instance ?: WallpaperRepository(context).also { instance = it }
            }
    }
}
