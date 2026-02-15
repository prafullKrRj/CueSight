package com.cuegight.cuesight.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Yellow theme colors for Material 3
val Yellow80 = Color(0xFFFFF59D)      // Light yellow
val YellowGrey80 = Color(0xFFFFF9C4) // Very light yellow
val Amber80 = Color(0xFFFFE082)       // Light amber

val Yellow40 = Color(0xFFFBC02D)      // Primary yellow
val YellowGrey40 = Color(0xFFF9A825)  // Golden yellow
val Amber40 = Color(0xFFFF8F00)       // Amber accent

// Legacy colors (keep for backwards compatibility)
val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

// New CueSight yellow-based color palette
object CueSightColors {
    // Primary yellow palette
    val Primary = Color(0xFFFBC02D)           // Vibrant yellow
    val PrimaryDark = Color(0xFFF9A825)       // Darker yellow
    val PrimaryLight = Color(0xFFFFF59D)      // Light yellow
    val Accent = Color(0xFFFF8F00)            // Amber accent
    
    // Supporting colors
    val Purple = Color(0xFF6C63FF)
    val Blue = Color(0xFF4A90E2)
    val Green = Color(0xFF4CAF50)
    val Orange = Color(0xFFFF9800)
    val Red = Color(0xFFFF5252)
    val Teal = Color(0xFF009688)
    
    // Gradients with yellow theme
    val PrimaryGradient = Brush.horizontalGradient(listOf(
        Color(0xFFFBC02D),  // Yellow
        Color(0xFFFF8F00)   // Amber
    ))
    val SuccessGradient = Brush.horizontalGradient(listOf(
        Color(0xFFFFEB3B),  // Bright yellow
        Color(0xFF4CAF50)   // Green
    ))
    val AccentGradient = Brush.horizontalGradient(listOf(
        Color(0xFFFF8F00),  // Amber
        Color(0xFFFF6F00)   // Deep orange
    ))
    
    // Emotion colors (keeping existing for semantic meaning)
    val EmotionHappy = Color(0xFFFDD835)      // Bright yellow
    val EmotionSad = Color(0xFF42A5F5)        // Blue
    val EmotionAngry = Color(0xFFFF6B6B)      // Red
    val EmotionSurprise = Color(0xFFFFA726)   // Orange
    val EmotionNeutral = Color(0xFF9E9E9E)    // Gray
    val EmotionDisgust = Color(0xFF66BB6A)    // Green
    val EmotionFear = Color(0xFF7E57C2)       // Purple
}
