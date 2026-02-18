package com.cuegight.cuesight.feature.analytics

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.column.columnChart
import com.patrykandpatrick.vico.compose.chart.line.lineChart
import com.patrykandpatrick.vico.core.entry.ChartEntryModel
import com.patrykandpatrick.vico.core.entry.FloatEntry
import com.patrykandpatrick.vico.core.entry.entryModelOf

/**
 * Professional chart components using Vico library
 */

/**
 * Learning Curve Line Chart
 * Shows accuracy progression across sessions
 */
@Composable
fun LearningCurveChart(
    accuracies: List<Float>,
    modifier: Modifier = Modifier
) {
    if (accuracies.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                "No data available",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
        return
    }

    val chartEntryModel = remember<ChartEntryModel>(accuracies) {
        entryModelOf(accuracies.mapIndexed { index, fl ->
            FloatEntry(x = index.toFloat(), y = fl * 100)
        })
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            "Learning Curve",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Chart(
            chart = lineChart(),
            model = chartEntryModel,
            startAxis = rememberStartAxis(
                valueFormatter = { value, _ -> "${value.toInt()}%" }
            ),
            bottomAxis = rememberBottomAxis(
                valueFormatter = { value, _ -> "S${value.toInt() + 1}" }
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp)
        )
    }
}

/**
 * Response Time Column Chart
 * Shows distribution of response times in buckets
 */
@Composable
fun ResponseTimeChart(
    distribution: Map<String, Int>,
    modifier: Modifier = Modifier
) {
    if (distribution.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("No response time data available")
        }
        return
    }

    // Sort keys or define specific order if needed
    val sortedEntries = distribution.entries.sortedBy { it.key }
    val counts = sortedEntries.map { it.value.toFloat() }
    val labels = sortedEntries.map { it.key }

    val chartEntryModel = remember(counts) {
        entryModelOf(*counts.toTypedArray())
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            "Response Time Distribution",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Chart(
            chart = columnChart(),
            model = chartEntryModel,
            startAxis = rememberStartAxis(),
            bottomAxis = rememberBottomAxis(
                valueFormatter = { value, _ ->
                    labels.getOrNull(value.toInt()) ?: ""
                }
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp)
        )
    }
}

/**
 * Accuracy Progress Bar Chart
 * Shows per-emotion accuracy as bars
 */
@Composable
fun EmotionAccuracyChart(
    emotionScores: Map<String, Float>,
    modifier: Modifier = Modifier
) {
    if (emotionScores.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("No emotion data available")
        }
        return
    }

    val sortedEmotions = remember(emotionScores) {
        emotionScores.toList().sortedByDescending { it.second }
    }

    val emotions = sortedEmotions.map { it.first }
    val scores = sortedEmotions.map { it.second * 100 }

    val chartEntryModel = remember(scores) {
        entryModelOf(*scores.toTypedArray())
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            "Per-Emotion Accuracy",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            color = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Chart(
            chart = columnChart(),
            model = chartEntryModel,
            startAxis = rememberStartAxis(
                valueFormatter = { value, _ -> "${value.toInt()}%" }
            ),
            bottomAxis = rememberBottomAxis(
                valueFormatter = { value, _ ->
                    emotions.getOrNull(value.toInt())?.take(4) ?: ""
                }
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp)
        )
    }
}
