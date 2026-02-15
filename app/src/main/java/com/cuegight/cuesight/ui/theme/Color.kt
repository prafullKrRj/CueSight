package com.cuegight.cuesight.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Legacy colors (keep for backwards compatibility)
val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

// New CueSight color palette
object CueSightColors {
    val Purple = Color(0xFF6C63FF)
    val Blue = Color(0xFF4A90E2)
    val Green = Color(0xFF4CAF50)
    val Orange = Color(0xFFFF9800)
    val Red = Color(0xFFFF5252)
    val Teal = Color(0xFF009688)
    
    // Gradients
    val PrimaryGradient = Brush.horizontalGradient(listOf(Purple, Blue))
    val SuccessGradient = Brush.horizontalGradient(listOf(Green, Teal))
    
    // Emotion colors
    val EmotionHappy = Color(0xFFFDD835)      // Bright yellow
    val EmotionSad = Color(0xFF42A5F5)        // Blue
    val EmotionAngry = Color(0xFFFF6B6B)      // Red
    val EmotionSurprise = Color(0xFFFFA726)   // Orange
    val EmotionNeutral = Color(0xFF9E9E9E)    // Gray
    val EmotionDisgust = Color(0xFF66BB6A)    // Green
    val EmotionFear = Color(0xFF7E57C2)       // Purple
}
