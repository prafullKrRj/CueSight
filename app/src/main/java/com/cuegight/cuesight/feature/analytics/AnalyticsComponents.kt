package com.cuegight.cuesight.feature.analytics

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cuegight.cuesight.ui.theme.CueSightColors

/**
 * Unique visualization components for analytics dashboard
 * Charts are in AnalyticsCharts.kt
 */

/**
 * Animated circular progress indicator showing mastery level
 */
@Composable
fun MasteryRing(
    mastery: Float,
    emotion: String,
    modifier: Modifier = Modifier
) {
    val animatedProgress = remember(emotion) { Animatable(0f) }

    LaunchedEffect(emotion, mastery) {
        animatedProgress.animateTo(
            targetValue = mastery,
            animationSpec = tween(durationMillis = 1000, easing = EaseOutCubic)
        )
    }
    
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(80.dp),
            contentAlignment = Alignment.Center
        ) {
            // Background circle
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ) {}
            
            // Progress circle
            CircularProgressIndicator(
                progress = { animatedProgress.value },
                modifier = Modifier.fillMaxSize(),
                color = getMasteryColor(mastery),
                strokeWidth = 8.dp,
                trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            )
            
            // Percentage text
            Text(
                text = "${(animatedProgress.value * 100).toInt()}%",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = getMasteryColor(mastery)
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = emotion,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Confusion Matrix Heatmap Grid
 */
@Composable
fun ConfusionMatrixHeatmap(
    confusionMatrix: Map<String, Map<String, Int>>,
    emotions: List<String>,
    modifier: Modifier = Modifier
) {
    if (emotions.isEmpty()) return
    
    // Find max value for color scaling
    val maxValue = remember(confusionMatrix) {
        confusionMatrix.values
            .flatMap { it.values }
            .maxOrNull() ?: 1
    }

    Column(modifier = modifier) {
        // Header row
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.size(60.dp)) {} // Empty corner
            emotions.forEach { emotion ->
                key(emotion) {
                    Box(
                        modifier = Modifier.size(60.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = emotion.take(3),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
        
        // Data rows
        emotions.forEach { actualEmotion ->
            key(actualEmotion) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    // Row label
                    Box(
                        modifier = Modifier.size(60.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = actualEmotion.take(3),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Data cells
                    emotions.forEach { predictedEmotion ->
                        key("${actualEmotion}_${predictedEmotion}") {
                            val count = confusionMatrix[actualEmotion]?.get(predictedEmotion) ?: 0
                            val intensity = if (maxValue > 0) count.toFloat() / maxValue else 0f

                            val cellColor = remember(actualEmotion, predictedEmotion, intensity) {
                                if (actualEmotion == predictedEmotion) {
                                    CueSightColors.Green.copy(alpha = 0.2f + (intensity * 0.6f))
                                } else {
                                    CueSightColors.Red.copy(alpha = 0.1f + (intensity * 0.5f))
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .size(60.dp)
                                    .background(cellColor)
                                    .border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = count.toString(),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = if (count > 0) FontWeight.Bold else FontWeight.Normal,
                                    color = if (intensity > 0.5f) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Animated stat card with icon
 */
@Composable
fun AnimatedStatCard(
    value: String,
    label: String,
    trend: Float? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
            
            trend?.let {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (it > 0) "↑ ${String.format("%.1f", it)}%" else "↓ ${String.format("%.1f", -it)}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (it > 0) CueSightColors.Green else CueSightColors.Red,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// Helper functions

private fun getMasteryColor(mastery: Float): Color {
    return when {
        mastery >= 0.8f -> CueSightColors.Green // High mastery
        mastery >= 0.6f -> CueSightColors.Orange // Moderate mastery
        else -> CueSightColors.Red // Low mastery
    }
}
