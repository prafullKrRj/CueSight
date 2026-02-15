package com.cuegight.cuesight.ui.theme

import android.app.Activity
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

private val DarkColorScheme = darkColorScheme(
    primary = Yellow80,
    secondary = Amber80,
    tertiary = YellowGrey80,
    background = Color(0xFF1C1B1F),
    surface = Color(0xFF1C1B1F),
    onPrimary = Color(0xFF3E2723),
    onSecondary = Color(0xFF3E2723),
    onTertiary = Color(0xFF3E2723),
    onBackground = Color(0xFFFFFBFE),
    onSurface = Color(0xFFFFFBFE),
    primaryContainer = Color(0xFFF9A825),
    onPrimaryContainer = Color(0xFF3E2723),
)

private val LightColorScheme = lightColorScheme(
    primary = Yellow40,
    secondary = Amber40,
    tertiary = YellowGrey40,
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color(0xFF3E2723),
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    primaryContainer = Color(0xFFFFF59D),
    onPrimaryContainer = Color(0xFF3E2723),
    secondaryContainer = Color(0xFFFFE082),
    onSecondaryContainer = Color(0xFF3E2723),
)

@Composable
fun CueSightTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false, // Disable dynamic color to use our yellow theme
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
        typography = Typography,
        content = content
    )
}