package com.cuegight.cuesight.feature.analytics

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import co.yml.charts.axis.AxisData
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
        
        val points = accuracies.mapIndexed { index, accuracy ->
            Point(index.toFloat(), accuracy)
        }
        
        val maxY = accuracies.maxOrNull()?.let { kotlin.math.ceil(it * 10) / 10 } ?: 1f
        val minY = 0f
        
        val xAxisData = AxisData.Builder()
            .axisStepSize(30.dp)
            .steps(accuracies.size - 1)
            .labelData { index -> "${index + 1}" }
            .labelAndAxisLinePadding(8.dp)
            .axisLineColor(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
            .axisLabelColor(MaterialTheme.colorScheme.onSurface)
            .build()
            
        val yAxisData = AxisData.Builder()
            .steps(5)
            .labelData { index -> 
                val value = minY + (index * (maxY - minY) / 5)
                String.format("%.1f", value)
            }
            .labelAndAxisLinePadding(12.dp)
            .axisLineColor(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
            .axisLabelColor(MaterialTheme.colorScheme.onSurface)
            .build()
        
        val lineChartData = LineChartData(
            linePlotData = LinePlotData(
                lines = listOf(
                    Line(
                        dataPoints = points,
                        lineStyle = LineStyle(
                            lineType = LineType.SmoothCurve()
                        ),
                        intersectionPoint = IntersectionPoint(
                            color = MaterialTheme.colorScheme.primary
                        ),
                        selectionHighlightPoint = SelectionHighlightPoint(
                            color = MaterialTheme.colorScheme.primary
                        ),
                        shadowUnderLine = ShadowUnderLine(
                            alpha = 0.3f,
                            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
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
            backgroundColor = Color.Transparent
        )
        
        LineChart(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            lineChartData = lineChartData
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
fun ResponseTimeChart(
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
        
        val barData = values.mapIndexed { index, value ->
            BarData(
                point = Point(index.toFloat(), value),
                color = MaterialTheme.colorScheme.primary,
                label = buckets[index]
            )
        }
        
        val maxY = values.maxOrNull()?.let { kotlin.math.ceil(it) } ?: 1f
        
        val xAxisData = AxisData.Builder()
            .axisStepSize(40.dp)
            .steps(buckets.size - 1)
            .labelData { index -> buckets.getOrNull(index) ?: "" }
            .labelAndAxisLinePadding(8.dp)
            .axisLineColor(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
            .axisLabelColor(MaterialTheme.colorScheme.onSurface)
            .build()
            
        val yAxisData = AxisData.Builder()
            .steps(5)
            .labelData { index -> 
                val value = index * maxY / 5
                String.format("%.0f", value)
            }
            .labelAndAxisLinePadding(12.dp)
            .axisLineColor(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
            .axisLabelColor(MaterialTheme.colorScheme.onSurface)
            .build()
        
        val barChartData = BarChartData(
            chartData = barData,
            xAxisData = xAxisData,
            yAxisData = yAxisData,
            barStyle = BarStyle(
                paddingBetweenBars = 12.dp,
                barWidth = 35.dp
            ),
            backgroundColor = Color.Transparent,
        )
        
        BarChart(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
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
        
        val barData = sortedEmotions.mapIndexed { index, (emotion, score) ->
            BarData(
                point = Point(index.toFloat(), score),
                color = getAccuracyColor(score),
                label = emotion.take(4)
            )
        }
        
        val xAxisData = AxisData.Builder()
            .axisStepSize(40.dp)
            .steps(sortedEmotions.size - 1)
            .labelData { index -> 
                sortedEmotions.getOrNull(index)?.first?.take(4) ?: ""
            }
            .labelAndAxisLinePadding(8.dp)
            .axisLineColor(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
            .axisLabelColor(MaterialTheme.colorScheme.onSurface)
            .build()
            
        val yAxisData = AxisData.Builder()
            .steps(5)
            .labelData { index -> 
                val value = index * 1.0f / 5
                String.format("%.1f", value)
            }
            .labelAndAxisLinePadding(12.dp)
            .axisLineColor(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
            .axisLabelColor(MaterialTheme.colorScheme.onSurface)
            .build()
        
        val barChartData = BarChartData(
            chartData = barData,
            xAxisData = xAxisData,
            yAxisData = yAxisData,
            barStyle = BarStyle(
                paddingBetweenBars = 8.dp,
                barWidth = 30.dp
            ),
            backgroundColor = Color.Transparent,
        )
        
        BarChart(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            barChartData = barChartData
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
