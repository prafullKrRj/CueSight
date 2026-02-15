package com.cuegight.cuesight.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Reusable gradient background component
 * Can be used for splash screens, headers, or decorative elements
 */
@Composable
fun GradientBackground(
    colors: List<Color>,
    modifier: Modifier = Modifier,
    isVertical: Boolean = true,
    content: @Composable () -> Unit = {}
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = if (isVertical) {
                    Brush.verticalGradient(colors)
                } else {
                    Brush.horizontalGradient(colors)
                }
            )
    ) {
        content()
    }
}
