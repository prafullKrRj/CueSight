package com.cuegight.cuesight.feature.analytics

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.min

/**
 * Enhanced visualization components for analytics dashboard
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
    val animatedProgress = remember { Animatable(0f) }
    
    LaunchedEffect(mastery) {
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
    val maxValue = confusionMatrix.values
        .flatMap { it.values }
        .maxOrNull() ?: 1
    
    Column(modifier = modifier) {
        // Header row
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.size(60.dp)) // Empty corner
            emotions.forEach { emotion ->
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
        
        // Data rows
        emotions.forEach { actualEmotion ->
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
                    val count = confusionMatrix[actualEmotion]?.get(predictedEmotion) ?: 0
                    val intensity = if (maxValue > 0) count.toFloat() / maxValue else 0f
                    
                    val cellColor = if (actualEmotion == predictedEmotion) {
                        // Diagonal (correct predictions) - green scale
                        Color(0xFF4CAF50).copy(alpha = 0.2f + (intensity * 0.6f))
                    } else {
                        // Off-diagonal (errors) - red scale
                        Color(0xFFF44336).copy(alpha = 0.1f + (intensity * 0.5f))
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

/**
 * Response Time Histogram
 */
@Composable
fun ResponseTimeHistogram(
    distribution: Map<String, Int>,
    modifier: Modifier = Modifier
) {
    val buckets = listOf("< 1s", "1-3s", "3-5s", "> 5s")
    val maxCount = distribution.values.maxOrNull() ?: 1
    
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        buckets.forEach { bucket ->
            val count = distribution[bucket] ?: 0
            val progress = if (maxCount > 0) count.toFloat() / maxCount else 0f
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = bucket,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.width(50.dp)
                )
                
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(32.dp)
                ) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxSize().clip(MaterialTheme.shapes.small),
                        color = getResponseTimeColor(bucket),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    
                    if (count > 0) {
                        Text(
                            text = count.toString(),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(horizontal = 8.dp),
                            color = if (progress > 0.3f) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

/**
 * Learning Curve Chart (Simplified line chart)
 */
@Composable
fun LearningCurveChart(
    accuracies: List<Float>,
    modifier: Modifier = Modifier
) {
    if (accuracies.isEmpty()) return
    
    Column(modifier = modifier.fillMaxWidth()) {
        // Chart area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    MaterialTheme.shapes.medium
                )
                .padding(16.dp)
        ) {
            // Simple visualization showing trend
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
            ) {
                accuracies.forEach { accuracy ->
                    val height = (accuracy * 100).coerceIn(0f, 100f)
                    Box(
                        modifier = Modifier
                            .width(20.dp)
                            .fillMaxHeight(height / 100f)
                            .background(
                                getMasteryColor(accuracy),
                                MaterialTheme.shapes.small
                            )
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // X-axis label
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Session 1",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Text(
                text = "Latest",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
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
                    color = if (it > 0) Color(0xFF4CAF50) else Color(0xFFF44336),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// Helper functions

private fun getMasteryColor(mastery: Float): Color {
    return when {
        mastery >= 0.8f -> Color(0xFF4CAF50) // Green
        mastery >= 0.6f -> Color(0xFFFF9800) // Orange
        else -> Color(0xFFF44336) // Red
    }
}

private fun getResponseTimeColor(bucket: String): Color {
    return when (bucket) {
        "< 1s" -> Color(0xFF4CAF50)
        "1-3s" -> Color(0xFF8BC34A)
        "3-5s" -> Color(0xFFFF9800)
        else -> Color(0xFFF44336)
    }
}
