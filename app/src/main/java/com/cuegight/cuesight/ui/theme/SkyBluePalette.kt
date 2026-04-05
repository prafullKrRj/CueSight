package com.cuegight.cuesight.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

object SkyBluePalette {
    val Sky900 = Color(0xFF0B3A64)
    val Sky700 = Color(0xFF1F6FB2)
    val Sky500 = Color(0xFF3EA3E6)
    val Sky300 = Color(0xFF7CC8F8)
    val Sky200 = Color(0xFFB6E1FF)
    val Sky100 = Color(0xFFD9EEFF)
    val Sky50 = Color(0xFFF2F9FF)

    val AccentMint = Color(0xFF7FD9D1)
    val AccentIndigo = Color(0xFF5B78C7)

    val SuccessBlue = Color(0xFF2A84D2)
    val WarningBlue = Color(0xFF4E94D4)

    val AppBackground = Brush.verticalGradient(
        colors = listOf(Sky100, Sky50)
    )

    val HeaderGradient = Brush.horizontalGradient(
        colors = listOf(Sky300, Sky500)
    )
}

