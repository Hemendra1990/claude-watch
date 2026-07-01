package com.hemendra.depthwallpaper.data

/**
 * User-tunable settings for the depth composition. Immutable; copy to change.
 *
 * @param clockColor ARGB int of the clock text.
 * @param showDate whether the date line is drawn under the time.
 * @param use24Hour 24-hour vs 12-hour clock formatting.
 * @param parallaxStrength 0f..1f multiplier for how far layers slide on tilt.
 * @param subjectPop 0f..1f multiplier for how much the subject counter-moves,
 *        exaggerating the separation from the background.
 * @param hasImage true once a photo has been processed and stored.
 */
data class WallpaperConfig(
    val clockColor: Int = 0xFFFFFFFF.toInt(),
    val showDate: Boolean = true,
    val use24Hour: Boolean = false,
    val parallaxStrength: Float = 0.6f,
    val subjectPop: Float = 0.5f,
    val hasImage: Boolean = false,
)
