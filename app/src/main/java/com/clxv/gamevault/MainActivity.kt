package com.clxv.gamevault

import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.clxv.gamevault.core.settings.SettingsManager
import com.clxv.gamevault.core.settings.ThemeColor
import com.clxv.gamevault.core.settings.ThemeMode
import com.clxv.gamevault.ui.navigation.AppNav
import com.clxv.gamevault.ui.theme.GameVaultTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settings: SettingsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val s by settings.settings.collectAsState(initial = null)
            val dark = when (s?.theme ?: ThemeMode.SYSTEM) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            GameVaultTheme(darkTheme = dark, themeColor = s?.themeColor ?: ThemeColor.DEFAULT) {
                val bgName = s?.background ?: "none"
                val bg by produceState<androidx.compose.ui.graphics.ImageBitmap?>(
                    initialValue = null, bgName,
                ) {
                    value = if (bgName == "none") null else runCatching {
                        assets.open("backgrounds/$bgName").use { input ->
                            BitmapFactory.decodeStream(input)?.asImageBitmap()
                        }
                    }.getOrNull()
                }

                Box(Modifier.fillMaxSize()) {
                    if (bg != null) {
                        // Wallpaper: cropped to fill (never stretched/distorted)
                        Image(
                            bitmap = bg!!,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                        // Readability scrim — keeps every text readable on any wallpaper
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(
                                    if (dark) Color.Black.copy(alpha = 0.52f)
                                    else Color.White.copy(alpha = 0.70f)
                                )
                        )
                    } else {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background)
                        )
                    }
                    // Content sits above the wallpaper; screens use transparent scaffolds
                    AppNav()
                }
            }
        }
    }
}
