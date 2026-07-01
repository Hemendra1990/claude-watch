package com.hemendra.depthwallpaper.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hemendra.depthwallpaper.data.WallpaperConfig
import kotlinx.coroutines.delay
import java.util.Calendar

/**
 * A live, phone-shaped preview of the depth composition. Layers background ▸
 * clock ▸ subject so the subject overlaps the clock exactly as the real
 * wallpaper renders it. (Parallax is device-motion only and not simulated here.)
 */
@Composable
fun DepthPreview(
    background: Bitmap?,
    foreground: Bitmap?,
    config: WallpaperConfig,
    modifier: Modifier = Modifier,
) {
    val clockColor = Color(config.clockColor)
    val time by rememberClockText(config.use24Hour)
    val date = rememberDateText()

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF12162E), Color(0xFF05060E)),
                ),
            ),
    ) {
        if (background != null) {
            Image(
                bitmap = background.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(top = 44.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = time,
                color = clockColor,
                fontSize = 64.sp,
                fontWeight = FontWeight.Thin,
                textAlign = TextAlign.Center,
            )
            if (config.showDate) {
                Text(
                    text = date,
                    color = clockColor,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Normal,
                )
            }
        }

        if (foreground != null) {
            Image(
                bitmap = foreground.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun rememberClockText(use24Hour: Boolean) =
    produceState(initialValue = formatTime(use24Hour), use24Hour) {
        while (true) {
            value = formatTime(use24Hour)
            delay(1_000)
        }
    }

@Composable
private fun rememberDateText(): String = formatDate()

private fun formatTime(use24Hour: Boolean): String {
    val cal = Calendar.getInstance()
    val minute = cal.get(Calendar.MINUTE)
    return if (use24Hour) {
        "%02d:%02d".format(cal.get(Calendar.HOUR_OF_DAY), minute)
    } else {
        var hour = cal.get(Calendar.HOUR)
        if (hour == 0) hour = 12
        "%d:%02d".format(hour, minute)
    }
}

private fun formatDate(): String {
    val days = arrayOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
    val months = arrayOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
    )
    val cal = Calendar.getInstance()
    return "${days[cal.get(Calendar.DAY_OF_WEEK) - 1]}, " +
        "${months[cal.get(Calendar.MONTH)]} ${cal.get(Calendar.DAY_OF_MONTH)}"
}
