package io.github.hyrumrichardson.luma.ui

import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import io.github.hyrumrichardson.luma.R

private val Night = Color(0xFF221654)
private val Violet = Color(0xFF6B4FD8)
private val Lavender = Color(0xFFCBBEFF)
private val Gold = Color(0xFFF2B33D)
private val PaleGold = Color(0xFFFFE08A)

private val LightColors = lightColorScheme(
    primary = Violet,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE6DEFF),
    onPrimaryContainer = Night,
    secondary = Color(0xFF8A5A00),
    secondaryContainer = PaleGold,
    onSecondaryContainer = Color(0xFF2B1D00),
)

private val DarkColors = darkColorScheme(
    primary = Lavender,
    onPrimary = Night,
    primaryContainer = Color(0xFF4B3191),
    onPrimaryContainer = Color(0xFFE6DEFF),
    secondary = Gold,
    secondaryContainer = Color(0xFF5C4300),
    onSecondaryContainer = PaleGold,
    background = Color(0xFF120C2E),
    surface = Color(0xFF181236),
    surfaceContainer = Color(0xFF211A45),
    surfaceContainerHigh = Color(0xFF2A2252),
)

@Composable
fun LumaTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}

/** Luma's star mascot, drawn from the launcher icon layers. */
@Composable
fun LumaAvatar(size: Dp, modifier: Modifier = Modifier, glow: Float = 0f) {
    Box(modifier.size(size).clip(CircleShape)) {
        // The adaptive icon layers are 108dp with the visible 72dp in the middle.
        Image(
            painterResource(R.drawable.ic_launcher_background), null,
            Modifier.size(size).scale(1.5f), contentScale = ContentScale.Crop,
        )
        Image(
            painterResource(R.drawable.ic_launcher_foreground), null,
            Modifier.size(size).scale(1.5f * (1f + 0.12f * glow)), contentScale = ContentScale.Crop,
        )
    }
}
