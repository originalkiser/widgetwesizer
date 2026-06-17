package com.widgetwesizer.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val TealPrimary = Color(0xFF1DB8A8)
private val TealOnPrimary = Color(0xFF003731)
private val TealPrimaryContainer = Color(0xFF00504A)
private val TealSecondary = Color(0xFF4DB6AC)
private val TealBackground = Color(0xFF0F1C1B)
private val TealSurface = Color(0xFF1A2928)
private val TealOnSurface = Color(0xFFE0F2F1)

private val DarkColorScheme = darkColorScheme(
    primary = TealPrimary,
    onPrimary = TealOnPrimary,
    primaryContainer = TealPrimaryContainer,
    secondary = TealSecondary,
    background = TealBackground,
    surface = TealSurface,
    onSurface = TealOnSurface,
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF006B61),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF9EF2E4),
    secondary = Color(0xFF4A635F),
    background = Color(0xFFF4FBF9),
    surface = Color(0xFFF4FBF9),
    onSurface = Color(0xFF161D1C),
)

@Composable
fun WidgetWesizerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = WidgetWesizerTypography,
        content = content
    )
}
