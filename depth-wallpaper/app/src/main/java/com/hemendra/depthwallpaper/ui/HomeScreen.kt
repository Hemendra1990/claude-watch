package com.hemendra.depthwallpaper.ui

import android.app.WallpaperManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hemendra.depthwallpaper.data.WallpaperConfig
import com.hemendra.depthwallpaper.ui.components.DepthPreview
import com.hemendra.depthwallpaper.wallpaper.DepthWallpaperService

private val CLOCK_COLORS = listOf(
    0xFFFFFFFF, 0xFF111111, 0xFFFFC857, 0xFF7BD3FF, 0xFFFF9DC4, 0xFF9DFFB0,
).map { it.toInt() }

@Composable
fun HomeScreen(viewModel: DepthViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> if (uri != null) viewModel.onImagePicked(uri) }

    fun pickImage() = picker.launch(
        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
    )

    fun applyWallpaper() {
        val component = ComponentName(context, DepthWallpaperService::class.java)
        val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
            .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component)
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            // Some OEMs don't honour the direct-preview extra; fall back to the
            // generic live-wallpaper chooser.
            context.startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            text = "Depth Wallpaper",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Box(contentAlignment = Alignment.Center) {
            DepthPreview(
                background = state.background,
                foreground = state.foreground,
                config = state.config,
                modifier = Modifier
                    .fillMaxWidth(0.72f)
                    .aspectRatio(9f / 19.5f),
            )
            if (state.process is ProcessState.Working) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text("Cutting out subject…", color = Color.White)
                }
            }
        }

        (state.process as? ProcessState.Error)?.let { error ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(error.message, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = viewModel::clearError) { Text("Dismiss") }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = ::pickImage,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(if (state.config.hasImage) "Change photo" else "Choose photo")
            }
            Button(
                onClick = ::applyWallpaper,
                enabled = state.config.hasImage,
                modifier = Modifier.weight(1f),
            ) {
                Text("Set as wallpaper")
            }
        }

        if (state.config.hasImage) {
            ClockSection(state.config, viewModel)
            DepthSection(state.config, viewModel)
        } else {
            Text(
                text = "Pick a photo with a clear subject (a person, pet or object) " +
                    "to build your depth wallpaper.",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun ClockSection(config: WallpaperConfig, viewModel: DepthViewModel) {
    SettingsCard(title = "Clock") {
        Text("Colour", color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CLOCK_COLORS.forEach { color ->
                val selected = color == config.clockColor
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(color))
                        .border(
                            width = if (selected) 3.dp else 1.dp,
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                Color.White.copy(alpha = 0.4f)
                            },
                            shape = CircleShape,
                        )
                        .clickable { viewModel.setClockColor(color) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = if (color == 0xFFFFFFFF.toInt()) Color.Black else Color.White,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        ToggleRow("Show date", config.showDate, viewModel::setShowDate)
        ToggleRow("24-hour time", config.use24Hour, viewModel::setUse24Hour)
    }
}

@Composable
private fun DepthSection(config: WallpaperConfig, viewModel: DepthViewModel) {
    SettingsCard(title = "Depth & motion") {
        SliderRow("Parallax strength", config.parallaxStrength, viewModel::setParallax)
        SliderRow("Subject pop", config.subjectPop, viewModel::setSubjectPop)
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(20.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurface)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SliderRow(label: String, value: Float, onChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, color = MaterialTheme.colorScheme.onSurface)
        Slider(value = value, onValueChange = onChange, valueRange = 0f..1f)
    }
}
