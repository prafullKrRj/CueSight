package com.cuegight.cuesight.feature.analytics

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberBottom
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberStart
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.common.of
import com.patrykandpatrick.vico.core.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.core.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.columnSeries
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.patrykandpatrick.vico.core.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.core.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.core.common.shape.Shape
import com.cuegight.cuesight.ui.theme.CueSightColors

/**
 * Professional chart components using Vico library
 */

/**
 * Learning Curve Line Chart
 * Shows accuracy progression across sessions
 */
@Composable
fun VicoLearningCurveChart(
    accuracies: List<Float>,
    modifier: Modifier = Modifier
) {
    if (accuracies.isEmpty()) {
        Text("No data available", modifier = modifier)
        return
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            "Learning Curve",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        val modelProducer = remember(accuracies) {
            CartesianChartModelProducer.build {
                lineSeries(accuracies.mapIndexed { index, accuracy -> index to accuracy })
            }
        }

        val primaryColor = MaterialTheme.colorScheme.primary.toArgb()
        val surfaceColor = MaterialTheme.colorScheme.surface.toArgb()
        
        CartesianChartHost(
            chart = rememberCartesianChart(
                rememberLineCartesianLayer(
                    LineCartesianLayer.LineProvider.series(
                        LineCartesianLayer.rememberLine(
                            fill = LineCartesianLayer.LineFill.single(
                                fill = androidx.compose.ui.graphics.SolidColor(
                                    MaterialTheme.colorScheme.primary
                                )
                            ),
                            thickness = 3.dp,
                            areaFill = LineCartesianLayer.AreaFill.single(
                                fill = androidx.compose.ui.graphics.SolidColor(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                )
                            )
                        )
                    )
                ),
                startAxis = VerticalAxis.rememberStart(
                    label = rememberTextComponent(
                        color = MaterialTheme.colorScheme.onSurface,
                        textSize = 12.dp
                    )
                ),
                bottomAxis = HorizontalAxis.rememberBottom(
                    label = rememberTextComponent(
                        color = MaterialTheme.colorScheme.onSurface,
                        textSize = 12.dp
                    )
                )
            ),
            modelProducer = modelProducer,
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "Session 1",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Text(
                "Latest",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

/**
 * Response Time Column Chart
 * Shows distribution of response times in buckets
 */
@Composable
fun VicoResponseTimeChart(
    distribution: Map<String, Int>,
    modifier: Modifier = Modifier
) {
    val buckets = listOf("< 1s", "1-3s", "3-5s", "> 5s")
    val values = buckets.map { distribution[it]?.toFloat() ?: 0f }
    
    if (values.all { it == 0f }) {
        Text("No response time data available", modifier = modifier)
        return
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            "Response Time Distribution",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        val modelProducer = remember(values) {
            CartesianChartModelProducer.build {
                columnSeries(values)
            }
        }
        
        CartesianChartHost(
            chart = rememberCartesianChart(
                rememberColumnCartesianLayer(
                    ColumnCartesianLayer.ColumnProvider.series(
                        ColumnCartesianLayer.rememberColumn(
                            fill = androidx.compose.ui.graphics.SolidColor(
                                MaterialTheme.colorScheme.primary
                            ),
                            shape = Shape.rounded(topStartPercent = 40, topEndPercent = 40)
                        )
                    )
                ),
                startAxis = VerticalAxis.rememberStart(
                    label = rememberTextComponent(
                        color = MaterialTheme.colorScheme.onSurface,
                        textSize = 12.dp
                    )
                ),
                bottomAxis = HorizontalAxis.rememberBottom(
                    label = rememberTextComponent(
                        color = MaterialTheme.colorScheme.onSurface,
                        textSize = 12.dp
                    ),
                    valueFormatter = { value, _, _ ->
                        buckets.getOrNull(value.toInt()) ?: ""
                    }
                )
            ),
            modelProducer = modelProducer,
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
        )
    }
}

/**
 * Accuracy Progress Bar Chart
 * Shows per-emotion accuracy as horizontal bars
 */
@Composable
fun VicoEmotionAccuracyChart(
    emotionScores: Map<String, Float>,
    modifier: Modifier = Modifier
) {
    if (emotionScores.isEmpty()) {
        Text("No emotion data available", modifier = modifier)
        return
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            "Per-Emotion Accuracy",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        val sortedEmotions = emotionScores.toList().sortedByDescending { it.second }
        val values = sortedEmotions.map { it.second }
        
        val modelProducer = remember(values) {
            CartesianChartModelProducer.build {
                columnSeries(values)
            }
        }
        
        CartesianChartHost(
            chart = rememberCartesianChart(
                rememberColumnCartesianLayer(
                    ColumnCartesianLayer.ColumnProvider.series(
                        sortedEmotions.mapIndexed { index, (_, score) ->
                            ColumnCartesianLayer.rememberColumn(
                                fill = androidx.compose.ui.graphics.SolidColor(
                                    getAccuracyColor(score)
                                ),
                                shape = Shape.rounded(topStartPercent = 40, topEndPercent = 40)
                            )
                        }
                    )
                ),
                startAxis = VerticalAxis.rememberStart(
                    label = rememberTextComponent(
                        color = MaterialTheme.colorScheme.onSurface,
                        textSize = 12.dp
                    )
                ),
                bottomAxis = HorizontalAxis.rememberBottom(
                    label = rememberTextComponent(
                        color = MaterialTheme.colorScheme.onSurface,
                        textSize = 10.dp
                    ),
                    valueFormatter = { value, _, _ ->
                        sortedEmotions.getOrNull(value.toInt())?.first?.take(4) ?: ""
                    }
                )
            ),
            modelProducer = modelProducer,
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
        )
    }
}

private fun getAccuracyColor(accuracy: Float): Color {
    return when {
        accuracy >= 0.8f -> Color(0xFF4CAF50) // Green
        accuracy >= 0.6f -> Color(0xFFFF9800) // Orange
        else -> Color(0xFFF44336) // Red
    }
}
