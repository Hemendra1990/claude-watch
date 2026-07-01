package com.hemendra.depthwallpaper.segmentation

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Wraps ML Kit's on-device Subject Segmentation. Given a photo, it returns a
 * bitmap containing only the salient subject(s) with a transparent background —
 * the "cut-out" layer that gets drawn in front of the clock to create depth.
 */
class SubjectSegmenter {

    private val client = SubjectSegmentation.getClient(
        SubjectSegmenterOptions.Builder()
            // We want the composited RGBA cut-out, not per-subject masks.
            .enableForegroundBitmap()
            .build(),
    )

    /**
     * @return an ARGB_8888 bitmap the same size as [source] with everything but
     *   the subject made transparent.
     * @throws SegmentationException if the model produced no foreground (e.g. no
     *   detectable subject in the photo).
     */
    suspend fun extractForeground(source: Bitmap): Bitmap =
        suspendCancellableCoroutine { cont ->
            val input = InputImage.fromBitmap(source, 0)
            client.process(input)
                .addOnSuccessListener { result ->
                    val foreground = result.foregroundBitmap
                    if (foreground != null) {
                        cont.resume(foreground)
                    } else {
                        cont.resumeWithException(
                            SegmentationException("No subject detected in the image."),
                        )
                    }
                }
                .addOnFailureListener { cont.resumeWithException(it) }
                .addOnCanceledListener { cont.cancel() }
        }

    fun close() = client.close()
}

class SegmentationException(message: String) : Exception(message)
