package com.cuegight.cuesight.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.cuegight.cuesight.data.model.SessionMode
import com.cuegight.cuesight.viewmodel.StudentViewModel
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import org.koin.androidx.compose.koinViewModel
import java.util.Locale

private const val AVERAGE_FORMAT = "%.1f"
private const val CHART_LABEL = "Emotions Detected"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StudentDetailScreen(
    studentId: Long,
    onNavigateBack: () -> Unit,
    onNavigateToSession: (Long, String) -> Unit,
    viewModel: StudentViewModel = koinViewModel()
) {
    val student by viewModel.getStudentById(studentId).collectAsState(initial = null)
    val sessions by viewModel.getSessionsByStudent(studentId).collectAsState(initial = emptyList())
    val orderedSessions = remember(sessions) { sessions.sortedBy { it.startTime } }
    val sessionChartEntries = remember(orderedSessions) {
        orderedSessions.mapIndexed { index, session ->
            Entry(index.toFloat(), session.totalEmotionsDetected.toFloat())
        }
    }
    val lineColor = MaterialTheme.colorScheme.primary.toArgb()
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f).toArgb()
    val chartData = remember(sessionChartEntries, lineColor) {
        LineData(
            LineDataSet(sessionChartEntries, CHART_LABEL).apply {
                color = lineColor
                lineWidth = 2f
                setDrawCircles(false)
                setDrawValues(false)
            }
        )
    }
    val totalDurationSeconds = remember(orderedSessions) {
        orderedSessions.sumOf { it.durationSeconds }
    }
    val totalDurationLabel = remember(totalDurationSeconds) {
        formatDuration(totalDurationSeconds)
    }
    val averageEmotions = remember(orderedSessions) {
        if (orderedSessions.isEmpty()) 0f else {
            orderedSessions.sumOf { it.totalEmotionsDetected }.toFloat() / orderedSessions.size
        }
    }
    val averageEmotionsLabel = remember(averageEmotions) {
        String.format(Locale.US, AVERAGE_FORMAT, averageEmotions)
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(student?.name ?: "Student Details") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (student == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Student Info Card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp)
                        ) {
                            Surface(
                                modifier = Modifier.size(80.dp),
                                shape = MaterialTheme.shapes.large,
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = student?.name?.firstOrNull()?.uppercase() ?: "?",
                                        style = MaterialTheme.typography.displayMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = student?.name ?: "",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Column {
                                    Text(
                                        text = "Age",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                    Text(
                                        text = "${student?.age} years",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                            if (student?.diagnosis?.isNotEmpty() == true) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Diagnosis",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                                Text(
                                    text = student?.diagnosis ?: "",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                            if (student?.notes?.isNotEmpty() == true) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Notes",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                                Text(
                                    text = student?.notes ?: "",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.ShowChart,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Progress Overview",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            if (orderedSessions.isEmpty()) {
                                Text(
                                    text = "No sessions yet. Start a session to see progress trends.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                )
                            } else {
                                AndroidView(
                                    factory = { context ->
                                        LineChart(context).apply {
                                            description.isEnabled = false
                                            setTouchEnabled(false)
                                            setScaleEnabled(false)
                                            setPinchZoom(false)
                                            legend.isEnabled = false
                                            axisRight.isEnabled = false
                                            axisLeft.axisMinimum = 0f
                                            axisLeft.textColor = labelColor
                                            axisLeft.gridColor = gridColor
                                            xAxis.position = XAxis.XAxisPosition.BOTTOM
                                            xAxis.granularity = 1f
                                            xAxis.textColor = labelColor
                                            xAxis.setDrawGridLines(false)
                                        }
                                    },
                                    update = { chart ->
                                        chart.axisLeft.textColor = labelColor
                                        chart.axisLeft.gridColor = gridColor
                                        chart.xAxis.textColor = labelColor
                                        chart.data = chartData
                                        chart.invalidate()
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    StatPill(text = "Sessions: ${orderedSessions.size}")
                                    StatPill(text = "Avg emotions: $averageEmotionsLabel")
                                    StatPill(text = "Total time: $totalDurationLabel")
                                }
                            }
                        }
                    }
                }
                
                // Session Modes Section
                item {
                    Text(
                        text = "Start Training Session",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                // Teaching Mode Card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.School,
                                    contentDescription = null,
                                    modifier = Modifier.size(40.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Teaching Mode",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Therapist demonstrates emotions to the student",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { onNavigateToSession(studentId, SessionMode.TEACHING.name) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Start Teaching Session")
                            }
                        }
                    }
                }
                
                // Practice Mode Card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Psychology,
                                    contentDescription = null,
                                    modifier = Modifier.size(40.dp),
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Practice Mode",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Student practices expressing emotions independently",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { onNavigateToSession(studentId, SessionMode.PRACTICE.name) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondary
                                )
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Start Practice Session")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatPill(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large,
        tonalElevation = 2.dp
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun formatDuration(totalSeconds: Long): String {
    if (totalSeconds < 0L) return "—"
    if (totalSeconds < 60L) return "0m"
    val totalMinutes = totalSeconds / 60
    val days = totalMinutes / (60 * 24)
    val hours = (totalMinutes % (60 * 24)) / 60
    val minutes = totalMinutes % 60
    val parts = mutableListOf<String>()
    if (days > 0) parts.add("${days}d")
    if (hours > 0) parts.add("${hours}h")
    if (minutes > 0) parts.add("${minutes}m")
    return parts.joinToString(" ")
}
