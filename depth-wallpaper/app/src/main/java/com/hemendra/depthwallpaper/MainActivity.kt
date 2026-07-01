package com.hemendra.depthwallpaper

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.hemendra.depthwallpaper.ui.HomeScreen
import com.hemendra.depthwallpaper.ui.theme.DepthWallpaperTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            DepthWallpaperTheme {
                Surface(modifier = Modifier.windowInsetsPadding(WindowInsets.systemBars)) {
                    HomeScreen()
                }
            }
        }
    }
}
