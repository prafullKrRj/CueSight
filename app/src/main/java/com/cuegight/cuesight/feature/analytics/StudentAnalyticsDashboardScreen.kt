package com.cuegight.cuesight.feature.analytics

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cuegight.cuesight.data.model.Student
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Student Analytics Dashboard Screen
 * Shows comprehensive analytics for a specific student including:
 * - ERPI (Learning Trajectory)
 * - Per-Emotion Mastery
 * - Confusion Matrix
 * - Response Time Analysis
 * - CWA (Confusion-Weighted Accuracy)
 * - Session Timeline
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentAnalyticsDashboardScreen(
    studentId: Long,
    onNavigateBack: () -> Unit,
    viewModel: StudentAnalyticsViewModel = koinViewModel { parametersOf(studentId) }
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val scope = rememberCoroutineScope()
    
    var showWeightDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    
    if (showWeightDialog && state.cwaResult != null) {
        WeightAdjustmentDialog(
            currentWeights = state.cwaResult!!.weights,
            onDismiss = { showWeightDialog = false },
            onSave = { weights ->
                scope.launch {
                    val success = viewModel.updateTherapistWeights(weights)
                    if (success) {
                        Toast.makeText(context, "Weights updated successfully", Toast.LENGTH_SHORT).show()
                        showWeightDialog = false
                    } else {
                        Toast.makeText(context, "Error: Weights must sum to 1.0", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = state.student?.name ?: "Student Analytics",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showExportDialog = true }) {
                        Icon(Icons.Default.Download, "Export Data")
                    }
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                state.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                state.error != null -> {
                    ErrorView(
                        message = state.error ?: "Unknown error",
                        onRetry = { viewModel.refresh() },
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                state.student == null -> {
                    EmptyDataView(
                        message = "Student not found",
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                state.sessionCount == 0 -> {
                    EmptyDataView(
                        message = "No practice sessions yet.\nComplete some practice sessions to see analytics.",
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                else -> {
                    StudentAnalyticsContent(
                        state = state,
                        onAdjustWeights = { showWeightDialog = true }
                    )
                }
            }
        }
    }
    
    if (showExportDialog) {
        ExportDataDialog(
            onDismiss = { showExportDialog = false },
            onExport = { format ->
                scope.launch {
                    val uri = viewModel.exportData(context, format)
                    if (uri != null) {
                        Toast.makeText(context, "Exported to Downloads", Toast.LENGTH_LONG).show()
                        showExportDialog = false
                    } else {
                        Toast.makeText(context, "Export failed", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }
}

@Composable
private fun StudentAnalyticsContent(
    state: StudentAnalyticsState,
    onAdjustWeights: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        // Student Profile Card
        item {
            StudentProfileCard(
                student = state.student!!,
                sessionCount = state.sessionCount,
                totalGuesses = state.totalGuesses,
                averageResponseTime = state.averageResponseTime
            )
        }
        
        // Overall Stats Card
        item {
            OverallStatsCard(
                erpiResult = state.erpiResult,
                cwaResult = state.cwaResult,
                overallAccuracy = state.overallAccuracy
            )
        }
        
        // Learning Trajectory (ERPI) with chart
        if (state.erpiResult != null && state.sessionAccuracies.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.TrendingUp, null, modifier = Modifier.size(32.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "Learning Trajectory (ERPI)",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        
                        Text(
                            text = "ERPI Score: %.3f".format(state.erpiResult.erpiScore),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                state.erpiResult.erpiScore > 0.0f -> Color(0xFF4CAF50)
                                state.erpiResult.erpiScore > -0.1f -> Color(0xFFFF9800)
                                else -> Color(0xFFF44336)
                            }
                        )
                        Spacer(Modifier.height(8.dp))
                        
                        Text(
                            text = state.erpiResult.interpretation,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(Modifier.height(16.dp))
                        
                        // Professional line chart
                        LearningCurveChart(
                            accuracies = state.sessionAccuracies,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
        
        // Per-Emotion Mastery
        if (state.masteryResult != null) {
            item {
                EmotionMasteryCard(masteryResult = state.masteryResult)
            }
        }
        
        // Confusion Matrix with enhanced heatmap
        if (state.confusionMatrix.isNotEmpty()) {
            item {
                ConfusionMatrixCardEnhanced(
                    confusionMatrix = state.confusionMatrix,
                    mostConfusedPairs = state.mostConfusedPairs
                )
            }
        }
        
        // Response Time Distribution with histogram
        if (state.responseTimeDistribution.isNotEmpty()) {
            item {
                ResponseTimeCardEnhanced(
                    distribution = state.responseTimeDistribution,
                    averageResponseTime = state.averageResponseTime,
                    fastestTime = state.fastestResponseTime,
                    slowestTime = state.slowestResponseTime
                )
            }
        }
        
        // CWA Insights
        if (state.cwaResult != null) {
            item {
                CWAInsightsCard(
                    cwaResult = state.cwaResult,
                    onAdjustWeights = onAdjustWeights
                )
            }
        }
        
        // Session Timeline
        if (state.sessionHistory.isNotEmpty()) {
            item {
                Text(
                    text = "Practice Session Timeline",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            items(state.sessionHistory) { session ->
                SessionHistoryCard(session = session)
            }
        }
    }
}

@Composable
private fun StudentProfileCard(
    student: Student,
    sessionCount: Int,
    totalGuesses: Int,
    averageResponseTime: Long
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            Surface(
                modifier = Modifier.size(80.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.primary
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = student.name.firstOrNull()?.uppercase() ?: "?",
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            // Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = student.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Age ${student.age} • ${sessionCount} sessions completed",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
                if (totalGuesses > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "$totalGuesses total guesses • Avg ${averageResponseTime}ms response",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

@Composable
private fun OverallStatsCard(
    erpiResult: ErpiResult?,
    cwaResult: CwaResult?,
    overallAccuracy: Float
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Text(
                text = "Quick Stats",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(
                    label = "ERPI",
                    value = erpiResult?.let { "%.3f".format(it.erpiScore) } ?: "—",
                    color = erpiResult?.let { 
                        when {
                            it.erpiScore > 0.0f -> Color(0xFF4CAF50)
                            it.erpiScore > -0.1f -> Color(0xFFFF9800)
                            else -> Color(0xFFF44336)
                        }
                    } ?: MaterialTheme.colorScheme.onSecondaryContainer
                )
                
                StatItem(
                    label = "Overall Accuracy",
                    value = "${(overallAccuracy * 100).toInt()}%",
                    color = when {
                        overallAccuracy >= 0.8f -> Color(0xFF4CAF50)
                        overallAccuracy >= 0.6f -> Color(0xFFFF9800)
                        else -> Color(0xFFF44336)
                    }
                )
                
                StatItem(
                    label = "CWA Score",
                    value = cwaResult?.let { "${(it.cwaScore * 100).toInt()}%" } ?: "—",
                    color = cwaResult?.let {
                        when {
                            it.cwaScore >= 0.7f -> Color(0xFF4CAF50)
                            it.cwaScore >= 0.5f -> Color(0xFFFF9800)
                            else -> Color(0xFFF44336)
                        }
                    } ?: MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

@Composable
private fun StatItem(
    label: String,
    value: String,
    color: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
        )
    }
}

// Placeholder composables for other cards - will be implemented fully
@Composable
private fun LearningTrajectoryCard(erpiResult: ErpiResult) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.TrendingUp, null, modifier = Modifier.size(32.dp))
                Spacer(Modifier.width(12.dp))
                Text(
                    "Learning Trajectory (ERPI)",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(16.dp))
            
            Text(
                text = "ERPI Score: %.3f".format(erpiResult.erpiScore),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = when {
                    erpiResult.erpiScore > 0.0f -> Color(0xFF4CAF50)
                    erpiResult.erpiScore > -0.1f -> Color(0xFFFF9800)
                    else -> Color(0xFFF44336)
                }
            )
            Spacer(Modifier.height(8.dp))
            
            Text(
                text = erpiResult.interpretation,
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(Modifier.height(8.dp))
            
            Text(
                text = "Improvement rate: %.4f per session".format(erpiResult.slope),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun EmotionMasteryCard(masteryResult: MasteryResult) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Psychology, null, modifier = Modifier.size(32.dp))
                Spacer(Modifier.width(12.dp))
                Text(
                    "Emotion Mastery",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Overall Mastery: ${(masteryResult.aggregateMastery * 100).toInt()}%",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(16.dp))
            
            // Display mastery rings in a grid
            val emotions = masteryResult.perEmotionScores.toList()
            emotions.chunked(3).forEach { rowEmotions ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    rowEmotions.forEach { (emotion, score) ->
                        MasteryRing(
                            mastery = score,
                            emotion = emotion,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                    // Fill remaining spaces if not divisible by 3
                    repeat(3 - rowEmotions.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Strongest:", style = MaterialTheme.typography.bodySmall)
                    Text(masteryResult.strongestEmotion, fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50))
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Weakest:", style = MaterialTheme.typography.bodySmall)
                    Text(masteryResult.weakestEmotion, fontWeight = FontWeight.Bold, color = Color(0xFFF44336))
                }
            }
        }
    }
}

@Composable
private fun EmotionMasteryBar(emotion: String, mastery: Float) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(emotion, style = MaterialTheme.typography.bodyMedium)
            Text("${(mastery * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        }
        LinearProgressIndicator(
            progress = { mastery },
            modifier = Modifier.fillMaxWidth().height(8.dp),
            color = when {
                mastery >= 0.8f -> Color(0xFF4CAF50)
                mastery >= 0.6f -> Color(0xFFFF9800)
                else -> Color(0xFFF44336)
            },
        )
    }
}

@Composable
private fun ConfusionMatrixCard(
    confusionMatrix: Map<String, Map<String, Int>>,
    mostConfusedPairs: List<Pair<String, String>>
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.GridOn, null, modifier = Modifier.size(32.dp))
                Spacer(Modifier.width(12.dp))
                Text(
                    "Confusion Matrix",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Most confused pairs:",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            mostConfusedPairs.take(3).forEach { (actual, guessed) ->
                Text(
                    text = "• Confuses $actual with $guessed",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 8.dp, top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun ResponseTimeCard(
    distribution: Map<String, Int>,
    averageResponseTime: Long,
    fastestTime: Long,
    slowestTime: Long
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Timer, null, modifier = Modifier.size(32.dp))
                Spacer(Modifier.width(12.dp))
                Text(
                    "Response Times",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Average", style = MaterialTheme.typography.bodySmall)
                    Text("${averageResponseTime}ms", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Fastest", style = MaterialTheme.typography.bodySmall)
                    Text("${fastestTime}ms", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Slowest", style = MaterialTheme.typography.bodySmall)
                    Text("${slowestTime}ms", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun CWAInsightsCard(
    cwaResult: CwaResult,
    onAdjustWeights: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Confusion-Weighted Accuracy (CWA)",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "${(cwaResult.cwaScore * 100).toInt()}%",
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onAdjustWeights) {
                Icon(Icons.Default.Settings, null)
                Spacer(Modifier.width(8.dp))
                Text("Adjust Emotion Weights")
            }
        }
    }
}

@Composable
private fun SessionHistoryCard(session: SessionHistoryItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = session.formattedDate,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${session.correctGuesses}/${session.totalGuesses} correct",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            
            // Accuracy badge
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = when {
                    session.accuracy >= 0.8f -> Color(0xFF4CAF50)
                    session.accuracy >= 0.6f -> Color(0xFFFF9800)
                    else -> Color(0xFFF44336)
                }
            ) {
                Text(
                    text = "${(session.accuracy * 100).toInt()}%",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun ErrorView(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            Icons.Default.Error,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Text(
            text = "Error Loading Analytics",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Button(onClick = onRetry) {
            Text("Retry")
        }
    }
}

@Composable
private fun EmptyDataView(
    message: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            Icons.Default.BarChart,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
        )
        Text(
            text = "No Data Yet",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
private fun WeightAdjustmentDialog(
    currentWeights: Map<String, Float>,
    onDismiss: () -> Unit,
    onSave: (Map<String, Float>) -> Unit
) {
    var weights by remember { mutableStateOf(currentWeights) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Adjust Emotion Weights") },
        text = {
            Column {
                Text(
                    "Adjust the importance of each emotion for clinical relevance. Weights must sum to 1.0.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                weights.forEach { (emotion, weight) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(emotion, style = MaterialTheme.typography.bodyMedium)
                        Text("%.2f".format(weight), style = MaterialTheme.typography.bodyMedium)
                    }
                    Slider(
                        value = weight,
                        onValueChange = { newWeight ->
                            weights = weights + (emotion to newWeight)
                        },
                        valueRange = 0f..1f,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                
                Text(
                    "Sum: %.2f".format(weights.values.sum()),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(weights) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun ExportDataDialog(
    onDismiss: () -> Unit,
    onExport: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export Analytics Data") },
        text = {
            Column {
                Text("Choose export format:", modifier = Modifier.padding(bottom = 16.dp))
                Button(
                    onClick = { onExport("CSV") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Export as CSV")
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { onExport("PDF") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = false // PDF export not yet implemented
                ) {
                    Text("Export as PDF (Coming Soon)")
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// Enhanced card components using AnalyticsComponents

@Composable
private fun ConfusionMatrixCardEnhanced(
    confusionMatrix: Map<String, Map<String, Int>>,
    mostConfusedPairs: List<Pair<String, String>>
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.GridOn, null, modifier = Modifier.size(32.dp))
                Spacer(Modifier.width(12.dp))
                Text(
                    "Confusion Matrix",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(16.dp))
            
            // Enhanced heatmap
            ConfusionMatrixHeatmap(
                confusionMatrix = confusionMatrix,
                emotions = confusionMatrix.keys.sorted(),
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(Modifier.height(16.dp))
            
            Text(
                text = "Most confused pairs:",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )
            mostConfusedPairs.take(3).forEach { (actual, guessed) ->
                Text(
                    text = "• Confuses $actual with $guessed",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 8.dp, top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun ResponseTimeCardEnhanced(
    distribution: Map<String, Int>,
    averageResponseTime: Long,
    fastestTime: Long,
    slowestTime: Long
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Timer, null, modifier = Modifier.size(32.dp))
                Spacer(Modifier.width(12.dp))
                Text(
                    "Response Times",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                AnimatedStatCard(
                    value = "${averageResponseTime}ms",
                    label = "Average",
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                AnimatedStatCard(
                    value = "${fastestTime}ms",
                    label = "Fastest",
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                AnimatedStatCard(
                    value = "${slowestTime}ms",
                    label = "Slowest",
                    modifier = Modifier.weight(1f)
                )
            }
            
            Spacer(Modifier.height(16.dp))
            
            // Professional column chart
            ResponseTimeChart(
                distribution = distribution,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// Data classes for UI state
data class SessionHistoryItem(
    val sessionId: String,
    val formattedDate: String,
    val totalGuesses: Int,
    val correctGuesses: Int,
    val accuracy: Float
)
