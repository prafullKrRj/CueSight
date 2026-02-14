package com.cuegight.cuesight.feature.practice.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun EmotionButton(
    emotion: String,
    color: Color,
    isSelected: Boolean = false,
    isEnabled: Boolean = true,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = isEnabled,
        modifier = Modifier.fillMaxWidth(),
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
            containerColor = if (isSelected) color else color.copy(alpha = 0.7f)
        )
    ) {
        Text(
            text = emotion,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
fun FeedbackCard(message: String, isCorrect: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isCorrect) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
        )
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

fun getEmotionColor(emotion: String): Color = when (emotion.lowercase()) {
    "happy" -> Color(0xFFFFD54F)
    "sad" -> Color(0xFF64B5F6)
    "angry" -> Color(0xFFE57373)
    "surprised" -> Color(0xFFBA68C8)
    else -> Color(0xFF90A4AE)
}

@Composable
fun WeightAdjustmentDialog(
    currentWeights: Map<String, Float>,
    onDismiss: () -> Unit,
    onSave: (Map<String, Float>) -> Unit
) {
    val edited = remember(currentWeights) {
        mutableStateMapOf<String, String>().apply {
            currentWeights.forEach { (emotion, weight) ->
                put(emotion, weight.toString())
            }
        }
    }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Adjust Emotion Weights") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                currentWeights.keys.sorted().forEach { emotion ->
                    OutlinedTextField(
                        value = edited[emotion] ?: "0.0",
                        onValueChange = { edited[emotion] = it },
                        label = { Text(emotion) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(edited.mapValues { it.value.toFloatOrNull() ?: 0f })
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun MasteryBars(perEmotionScores: Map<String, Float>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        perEmotionScores.toSortedMap().forEach { (emotion, score) ->
            Column {
                Row {
                    Text(emotion, modifier = Modifier.weight(1f))
                    Text("${(score * 100).toInt()}%")
                }
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .background(Color.LightGray, RoundedCornerShape(8.dp))
                ) {
                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth(score.coerceIn(0f, 1f))
                            .height(8.dp)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                    )
                }
            }
        }
    }
}

@Composable
fun ConfusionMatrixGrid(matrix: Map<String, Map<String, Int>>, emotions: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Actual vs Predicted", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        emotions.forEach { actual ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(actual, modifier = Modifier.width(84.dp), fontWeight = FontWeight.Medium)
                emotions.forEach { predicted ->
                    Text(
                        text = (matrix[actual]?.get(predicted) ?: 0).toString(),
                        modifier = Modifier.width(24.dp)
                    )
                }
            }
        }
    }
}
