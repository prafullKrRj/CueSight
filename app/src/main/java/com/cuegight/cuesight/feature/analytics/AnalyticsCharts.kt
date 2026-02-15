package com.cuegight.cuesight.feature.analytics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import co.yml.charts.axis.AxisData
import com.cuegight.cuesight.ui.theme.CueSightColors
import co.yml.charts.common.model.Point
import co.yml.charts.ui.barchart.BarChart
import co.yml.charts.ui.barchart.models.BarChartData
import co.yml.charts.ui.barchart.models.BarData
import co.yml.charts.ui.barchart.models.BarStyle
import co.yml.charts.ui.linechart.LineChart
import co.yml.charts.ui.linechart.model.GridLines
import co.yml.charts.ui.linechart.model.IntersectionPoint
import co.yml.charts.ui.linechart.model.Line
import co.yml.charts.ui.linechart.model.LineChartData
import co.yml.charts.ui.linechart.model.LinePlotData
import co.yml.charts.ui.linechart.model.LineStyle
import co.yml.charts.ui.linechart.model.LineType
import co.yml.charts.ui.linechart.model.SelectionHighlightPoint
import co.yml.charts.ui.linechart.model.SelectionHighlightPopUp
import co.yml.charts.ui.linechart.model.ShadowUnderLine

/**
 * Professional chart components using YCharts library
 * Replacement for Vico charts with better Compose integration
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
        Text(
            "No data available",
            modifier = modifier,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        return
    }

    val points = remember(accuracies) {
        accuracies.mapIndexed { index, accuracy ->
            Point(index.toFloat(), accuracy)
        }
    }

    val maxY = remember(accuracies) {
        accuracies.maxOrNull()?.let { kotlin.math.ceil(it * 10) / 10 } ?: 1f
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
        
        val xAxisData = AxisData.Builder()
            .axisStepSize(50.dp)
            .steps(accuracies.size - 1)
            .labelData { index -> "S${index + 1}" }
            .labelAndAxisLinePadding(15.dp)
            .axisLineColor(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
            .axisLabelColor(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
            .build()
            
        val yAxisData = AxisData.Builder()
            .steps(5)
            .labelData { index -> 
                val value = 0f + (index * (maxY - 0f) / 5)
                String.format("%.1f", value)
            }
            .labelAndAxisLinePadding(16.dp)
            .axisLineColor(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
            .axisLabelColor(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
            .build()
        
        val lineChartData = LineChartData(
            linePlotData = LinePlotData(
                lines = listOf(
                    Line(
                        dataPoints = points,
                        lineStyle = LineStyle(
                            color = MaterialTheme.colorScheme.primary,
                            lineType = LineType.SmoothCurve(isDotted = false),
                            width = 3f
                        ),
                        intersectionPoint = IntersectionPoint(
                            color = MaterialTheme.colorScheme.primary,
                            radius = 4.dp
                        ),
                        selectionHighlightPoint = SelectionHighlightPoint(
                            color = MaterialTheme.colorScheme.tertiary,
                            radius = 6.dp
                        ),
                        shadowUnderLine = ShadowUnderLine(
                            alpha = 0.4f,
                            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                                    Color.Transparent
                                )
                            )
                        ),
                        selectionHighlightPopUp = SelectionHighlightPopUp()
                    )
                )
            ),
            xAxisData = xAxisData,
            yAxisData = yAxisData,
            gridLines = GridLines(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)),
            backgroundColor = Color.Transparent
        )
        
        LineChart(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 200.dp, max = 280.dp)
                .padding(top = 8.dp),
            lineChartData = lineChartData
        )
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "Session 1",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Text(
                "Latest",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
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
    val buckets = remember { listOf("< 1s", "1-3s", "3-5s", "> 5s") }
    val values = remember(distribution) {
        buckets.map { distribution[it]?.toFloat() ?: 0f }
    }

    if (values.all { it == 0f }) {
        Text(
            "No response time data available",
            modifier = modifier,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        return
    }

    val maxY = remember(values) {
        values.maxOrNull()?.let { kotlin.math.ceil(it) } ?: 1f
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
        
        val barData = values.mapIndexed { index, value ->
            BarData(
                point = Point(index.toFloat(), value),
                color = getResponseTimeColor(buckets[index]),
                label = buckets[index]
            )
        }
        
        val xAxisData = AxisData.Builder()
            .axisStepSize(60.dp)
            .steps(buckets.size - 1)
            .labelData { index -> buckets.getOrNull(index) ?: "" }
            .labelAndAxisLinePadding(15.dp)
            .axisLineColor(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
            .axisLabelColor(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
            .build()
            
        val yAxisData = AxisData.Builder()
            .steps(5)
            .labelData { index -> 
                val value = index * maxY / 5
                String.format("%.0f", value)
            }
            .labelAndAxisLinePadding(16.dp)
            .axisLineColor(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
            .axisLabelColor(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
            .build()
        
        val barChartData = BarChartData(
            chartData = barData,
            xAxisData = xAxisData,
            yAxisData = yAxisData,
            barStyle = BarStyle(
                paddingBetweenBars = 16.dp,
                barWidth = 40.dp
            ),
            backgroundColor = Color.Transparent,
        )
        
        BarChart(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 200.dp, max = 280.dp)
                .padding(top = 8.dp),
            barChartData = barChartData
        )
    }
}

/**
 * Accuracy Progress Bar Chart
 * Shows per-emotion accuracy as bars with color coding
 */
@Composable
fun EmotionAccuracyChart(
    emotionScores: Map<String, Float>,
    modifier: Modifier = Modifier
) {
    if (emotionScores.isEmpty()) {
        Text(
            "No emotion data available",
            modifier = modifier,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        return
    }

    val sortedEmotions = remember(emotionScores) {
        emotionScores.toList().sortedByDescending { it.second }
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
        
        val barData = sortedEmotions.mapIndexed { index, (emotion, score) ->
            BarData(
                point = Point(index.toFloat(), score),
                color = getAccuracyColor(score),
                label = emotion.take(4)
            )
        }
        
        val xAxisData = AxisData.Builder()
            .axisStepSize(55.dp)
            .steps(sortedEmotions.size - 1)
            .labelData { index ->
                sortedEmotions.getOrNull(index)?.first?.take(5) ?: ""
            }
            .labelAndAxisLinePadding(15.dp)
            .axisLineColor(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
            .axisLabelColor(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
            .build()
            
        val yAxisData = AxisData.Builder()
            .steps(5)
            .labelData { index -> 
                val value = index * 1.0f / 5
                String.format("%.1f", value)
            }
            .labelAndAxisLinePadding(16.dp)
            .axisLineColor(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
            .axisLabelColor(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
            .build()
        
        val barChartData = BarChartData(
            chartData = barData,
            xAxisData = xAxisData,
            yAxisData = yAxisData,
            barStyle = BarStyle(
                paddingBetweenBars = 12.dp,
                barWidth = 38.dp
            ),
            backgroundColor = Color.Transparent,
        )
        
        BarChart(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 200.dp, max = 280.dp)
                .padding(top = 8.dp),
            barChartData = barChartData
        )
    }
}

private fun getAccuracyColor(accuracy: Float): Color {
    return when {
        accuracy >= 0.8f -> CueSightColors.SuccessGreen // High accuracy - Dark Green
        accuracy >= 0.6f -> CueSightColors.WarningOrange // Moderate accuracy - Orange
        else -> CueSightColors.ErrorRed // Low accuracy - Red
    }
}

private fun getResponseTimeColor(bucket: String): Color {
    return when (bucket) {
        "< 1s" -> CueSightColors.SuccessGreen // Fast - Dark Green
        "1-3s" -> CueSightColors.GoodGreen // Good - Light Green
        "3-5s" -> CueSightColors.WarningOrange // Slow - Orange
        else -> CueSightColors.ErrorRed // Very Slow - Red
    }
}
