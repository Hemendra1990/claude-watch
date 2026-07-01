package com.hemendra.depthwallpaper.wallpaper

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import java.util.Calendar

/**
 * Draws the lock-screen-style clock (time + optional date) that the subject
 * cut-out is painted on top of. Kept deliberately simple and allocation-free on
 * the draw path so it is cheap to redraw every second.
 */
class ClockRenderer {

    private val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
        setShadowLayer(24f, 0f, 8f, Color.argb(140, 0, 0, 0))
    }

    private val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
        setShadowLayer(16f, 0f, 6f, Color.argb(140, 0, 0, 0))
    }

    private val monthNames = arrayOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
    )
    private val dayNames = arrayOf(
        "Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat",
    )

    /**
     * @param centerX horizontal centre to align the clock to.
     * @param topY baseline anchor near the top of the screen (lock-screen style).
     */
    fun draw(
        canvas: Canvas,
        centerX: Float,
        topY: Float,
        color: Int,
        use24Hour: Boolean,
        showDate: Boolean,
    ) {
        val now = Calendar.getInstance()

        timePaint.color = color
        timePaint.textSize = topY * 0.9f // scales with the anchor => screen size
        datePaint.color = color
        datePaint.textSize = timePaint.textSize * 0.22f

        val time = formatTime(now, use24Hour)
        canvas.drawText(time, centerX, topY + timePaint.textSize * 0.35f, timePaint)

        if (showDate) {
            val date = formatDate(now)
            val dateY = topY + timePaint.textSize * 0.35f + datePaint.textSize * 1.6f
            canvas.drawText(date, centerX, dateY, datePaint)
        }
    }

    private fun formatTime(cal: Calendar, use24Hour: Boolean): String {
        val minute = cal.get(Calendar.MINUTE)
        return if (use24Hour) {
            val hour = cal.get(Calendar.HOUR_OF_DAY)
            "%02d:%02d".format(hour, minute)
        } else {
            var hour = cal.get(Calendar.HOUR)
            if (hour == 0) hour = 12
            "%d:%02d".format(hour, minute)
        }
    }

    private fun formatDate(cal: Calendar): String {
        val dow = dayNames[cal.get(Calendar.DAY_OF_WEEK) - 1]
        val month = monthNames[cal.get(Calendar.MONTH)]
        val day = cal.get(Calendar.DAY_OF_MONTH)
        return "$dow, $month $day"
    }
}
