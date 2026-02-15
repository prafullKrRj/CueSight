package com.cuegight.cuesight.core.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cuegight.cuesight.core.util.EmotionMapper

/**
 * Reusable emotion selection button
 * Displays emotion with emoji and handles selection
 */
@Composable
fun EmotionButton(
    emotion: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    variant: EmotionButtonVariant = EmotionButtonVariant.FILLED
) {
    when (variant) {
        EmotionButtonVariant.FILLED -> {
            Button(
                onClick = onClick,
                modifier = modifier.fillMaxWidth(),
                enabled = enabled
            ) {
                Text(
                    text = "${EmotionMapper.getEmotionEmoji(emotion)} $emotion",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
            }
        }
        EmotionButtonVariant.OUTLINED -> {
            OutlinedButton(
                onClick = onClick,
                modifier = modifier.fillMaxWidth(),
                enabled = enabled
            ) {
                Text(
                    text = "${EmotionMapper.getEmotionEmoji(emotion)} $emotion",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

enum class EmotionButtonVariant {
    FILLED,
    OUTLINED
}

/**
 * Grid of emotion buttons
 */
@Composable
fun EmotionButtonGrid(
    emotions: List<String> = EmotionMapper.getAllEmotions(),
    onEmotionSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    variant: EmotionButtonVariant = EmotionButtonVariant.FILLED
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        emotions.forEach { emotion ->
            EmotionButton(
                emotion = emotion,
                onClick = { onEmotionSelected(emotion) },
                variant = variant
            )
        }
    }
}
