package com.hemendra.depthwallpaper.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hemendra.depthwallpaper.data.WallpaperConfig
import com.hemendra.depthwallpaper.data.WallpaperRepository
import com.hemendra.depthwallpaper.segmentation.SubjectSegmenter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max

/** Processing status for the subject-segmentation step. */
sealed interface ProcessState {
    data object Idle : ProcessState
    data object Working : ProcessState
    data class Error(val message: String) : ProcessState
}

data class EditorUiState(
    val config: WallpaperConfig = WallpaperConfig(),
    val process: ProcessState = ProcessState.Idle,
    val background: Bitmap? = null,
    val foreground: Bitmap? = null,
)

class DepthViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = WallpaperRepository.get(app)
    private val segmenter = SubjectSegmenter()

    private val _state = MutableStateFlow(EditorUiState(config = repository.currentConfig()))
    val state: StateFlow<EditorUiState> = _state.asStateFlow()

    init {
        // Restore whatever is currently applied so the editor opens on it.
        viewModelScope.launch {
            val bg = withContext(Dispatchers.IO) { repository.loadBackground() }
            val fg = withContext(Dispatchers.IO) { repository.loadForeground() }
            _state.value = _state.value.copy(background = bg, foreground = fg)
        }
    }

    /** Decode the picked photo, cut out the subject, and persist both layers. */
    fun onImagePicked(uri: Uri) {
        _state.value = _state.value.copy(process = ProcessState.Working)
        viewModelScope.launch {
            try {
                val source = withContext(Dispatchers.IO) { decodeScaled(uri) }
                    ?: throw IllegalStateException("Could not read that image.")
                val foreground = segmenter.extractForeground(source)
                withContext(Dispatchers.IO) { repository.saveLayers(source, foreground) }
                _state.value = _state.value.copy(
                    process = ProcessState.Idle,
                    background = source,
                    foreground = foreground,
                    config = repository.currentConfig(),
                )
            } catch (t: Throwable) {
                _state.value = _state.value.copy(
                    process = ProcessState.Error(
                        t.message ?: "Couldn't process the image.",
                    ),
                )
            }
        }
    }

    fun clearError() {
        if (_state.value.process is ProcessState.Error) {
            _state.value = _state.value.copy(process = ProcessState.Idle)
        }
    }

    // --- config setters (persisted; wallpaper reacts live) ----------------

    fun setClockColor(color: Int) = update { it.copy(clockColor = color) }
    fun setShowDate(show: Boolean) = update { it.copy(showDate = show) }
    fun setUse24Hour(use: Boolean) = update { it.copy(use24Hour = use) }
    fun setParallax(value: Float) = update { it.copy(parallaxStrength = value) }
    fun setSubjectPop(value: Float) = update { it.copy(subjectPop = value) }

    private fun update(transform: (WallpaperConfig) -> WallpaperConfig) {
        repository.updateConfig(transform)
        _state.value = _state.value.copy(config = repository.currentConfig())
    }

    override fun onCleared() {
        super.onCleared()
        segmenter.close()
    }

    /** Decode a content Uri, downscaling so segmentation stays fast/low-memory. */
    private fun decodeScaled(uri: Uri): Bitmap? {
        val resolver = getApplication<Application>().contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }

        val longest = max(bounds.outWidth, bounds.outHeight)
        var sample = 1
        while (longest / sample > MAX_DIMENSION) sample *= 2

        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        }
    }

    private companion object {
        const val MAX_DIMENSION = 2048
    }
}
