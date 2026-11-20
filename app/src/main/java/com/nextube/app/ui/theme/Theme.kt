package com.nextube.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.nextube.app.data.ThemeMode

// Matches the NexTube logo: signal red on near-black.
val BrandRed = Color(0xFFFF1E2D)
val BrandRedDeep = Color(0xFFC40015)
val BrandGradient = Brush.linearGradient(listOf(BrandRed, BrandRedDeep))

private val Light = lightColorScheme(
    primary = Color(0xFFD7001A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD7),
    onPrimaryContainer = Color(0xFF410004),
    secondary = Color(0xFF4A4547),
    secondaryContainer = Color(0xFFFFE3E1),
    onSecondaryContainer = Color(0xFF3B0A0D),
    background = Color(0xFFFFFBFA),
    surface = Color(0xFFFFFBFA),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFBF1F0),
    surfaceContainer = Color(0xFFF6EBEA),
    surfaceContainerHigh = Color(0xFFF0E5E4),
    surfaceContainerHighest = Color(0xFFEADFDE),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFFF5A61),
    onPrimary = Color(0xFF5C0008),
    primaryContainer = Color(0xFF8E0011),
    onPrimaryContainer = Color(0xFFFFDAD7),
    secondary = Color(0xFFE7BDBA),
    secondaryContainer = Color(0xFF3A1E20),
    onSecondaryContainer = Color(0xFFFFDAD7),
    background = Color(0xFF0B090A),
    surface = Color(0xFF0B090A),
    surfaceContainerLowest = Color(0xFF060405),
    surfaceContainerLow = Color(0xFF141112),
    surfaceContainer = Color(0xFF191516),
    surfaceContainerHigh = Color(0xFF231F20),
    surfaceContainerHighest = Color(0xFF2E292A),
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
)

@Composable
fun NexTubeTheme(mode: ThemeMode, dynamicColor: Boolean = false, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        dark -> Dark
        else -> Light
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    MaterialTheme(colorScheme = scheme, shapes = AppShapes, content = content)
}
