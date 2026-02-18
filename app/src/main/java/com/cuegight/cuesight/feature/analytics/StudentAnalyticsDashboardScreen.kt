package com.cuegight.cuesight.feature.analytics

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.cuegight.cuesight.data.model.Student
import com.cuegight.cuesight.feature.practice.domain.model.CwaResult
import com.cuegight.cuesight.feature.practice.domain.model.ErpiResult
import com.cuegight.cuesight.feature.practice.domain.model.MasteryResult
import com.cuegight.cuesight.feature.practice.ui.components.WeightAdjustmentDialog
import com.cuegight.cuesight.ui.theme.CueSightColors
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import java.io.File

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
        verticalArrangement = Arrangement.spacedBy(24.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp)
    ) {
        // Student Profile Card - Always present
        item(key = "profile_card") {
            StudentProfileCard(
                student = state.student!!,
                sessionCount = state.sessionCount,
                totalGuesses = state.totalGuesses,
                averageResponseTime = state.averageResponseTime
            )
        }

        // Overall Stats Card - Always present
        item(key = "overall_stats") {
            OverallStatsCard(
                erpiResult = state.erpiResult,
                cwaResult = state.cwaResult,
                overallAccuracy = state.overallAccuracy
            )
        }

        // Learning Trajectory - Conditionally render entire item
        if (state.erpiResult != null && state.sessionAccuracies.isNotEmpty()) {
            item(key = "learning_curve") {
                LearningTrajectoryCardEnhanced(
                    erpiResult = state.erpiResult,
                    sessionAccuracies = state.sessionAccuracies
                )
            }
        }

        // Per-Emotion Mastery - Conditionally render entire item
        if (state.masteryResult != null) {
            item(key = "emotion_mastery") {
                EmotionMasteryCard(masteryResult = state.masteryResult)
            }
        }

        // Confusion Matrix - Conditionally render entire item
        if (state.confusionMatrix.isNotEmpty()) {
            item(key = "confusion_matrix") {
                ConfusionMatrixCardEnhanced(
                    confusionMatrix = state.confusionMatrix,
                    mostConfusedPairs = state.mostConfusedPairs
                )
            }
        }

        // Response Time - Conditionally render entire item
        if (state.responseTimeDistribution.isNotEmpty()) {
            item(key = "response_time") {
                ResponseTimeCardEnhanced(
                    distribution = state.responseTimeDistribution,
                    averageResponseTime = state.averageResponseTime,
                    fastestTime = state.fastestResponseTime,
                    slowestTime = state.slowestResponseTime
                )
            }
        }

        // CWA Insights - Conditionally render entire item
        if (state.cwaResult != null) {
            item(key = "cwa_insights") {
                CWAInsightsCard(
                    cwaResult = state.cwaResult,
                    onAdjustWeights = onAdjustWeights
                )
            }
        }

        // Session History Header and Items - Conditionally render
        if (state.sessionHistory.isNotEmpty()) {
            item(key = "history_header") {
                Text(
                    text = "History",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
                )
            }

            // Session History Items
            items(
                items = state.sessionHistory,
                key = { session -> "session_${session.sessionId}" }
            ) { session ->
                SessionHistoryCard(session = session)
            }
        }
    }
}

@Composable
private fun LearningTrajectoryCardEnhanced(
    erpiResult: ErpiResult,
    sessionAccuracies: List<Float>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.TrendingUp,
                            null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Spacer(Modifier.width(16.dp))
                Text(
                    "Learning Trajectory",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(24.dp))

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "%.3f".format(erpiResult.erpiScore),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = getErpiScoreColor(erpiResult.erpiScore)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "ERPI Score",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = erpiResult.interpretation,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))

            // Professional line chart
            LearningCurveChart(
                accuracies = sessionAccuracies,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            )
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
    val context = LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar with photo or initial
            Surface(
                modifier = Modifier.size(72.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f),
                border = androidx.compose.foundation.BorderStroke(
                    2.dp,
                    MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.5f)
                )
            ) {
                if (!student.photoUri.isNullOrEmpty() && File(student.photoUri).exists()) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(File(student.photoUri))
                            .crossfade(true)
                            .build(),
                        contentDescription = "Photo of ${student.name}",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = student.name.firstOrNull()?.uppercase() ?: "?",
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = student.name,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = "Age ${student.age}",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "$sessionCount Sessions",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f)
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
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Text(
                text = "Performance Overview",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatItem(
                    label = "ERPI",
                    value = erpiResult?.let { "%.2f".format(it.erpiScore) } ?: "—",
                    color = erpiResult?.let { getErpiScoreColor(it.erpiScore) }
                        ?: MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )

                // Vertical divider
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(40.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                        .align(Alignment.CenterVertically)
                )

                StatItem(
                    label = "Accuracy",
                    value = "${(overallAccuracy * 100).toInt()}%",
                    color = getAccuracyColor(overallAccuracy),
                    modifier = Modifier.weight(1f)
                )

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(40.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                        .align(Alignment.CenterVertically)
                )

                StatItem(
                    label = "CWA Score",
                    value = cwaResult?.let { "${(it.cwaScore * 100).toInt()}%" } ?: "—",
                    color = cwaResult?.let { getCwaScoreColor(it.cwaScore) }
                        ?: MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun StatItem(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )
    }
}

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
                color = getErpiScoreColor(erpiResult.erpiScore)
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Psychology,
                            null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        "Emotion Mastery",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${(masteryResult.aggregateMastery * 100).toInt()}% Overall",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // Chart placeholder/implementation
            EmotionAccuracyChart(
                emotionScores = masteryResult.perEmotionScores,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            )

            Spacer(Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                EmotionInsightBox(
                    label = "Strongest Area",
                    emotion = masteryResult.strongestEmotion,
                    color = CueSightColors.Green,
                    modifier = Modifier.weight(1f)
                )
                EmotionInsightBox(
                    label = "Needs Focus",
                    emotion = masteryResult.weakestEmotion,
                    color = CueSightColors.Red,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun EmotionInsightBox(
    label: String,
    emotion: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = color.copy(alpha = 0.1f),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = emotion,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = color,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun AnimatedStatCard(
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Weighted Accuracy (CWA)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Adjusted for clinical relevance",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${(cwaResult.cwaScore * 100).toInt()}%",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = getCwaScoreColor(cwaResult.cwaScore)
                )
                TextButton(
                    onClick = onAdjustWeights,
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Text("Adjust Weights")
                }
            }
        }
    }
}

@Composable
private fun SessionHistoryCard(session: SessionHistoryItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "${session.correctGuesses}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        text = session.formattedDate,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${session.totalGuesses} total attempts",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Accuracy badge
            Surface(
                shape = MaterialTheme.shapes.small,
                color = getAccuracyColor(session.accuracy).copy(alpha = 0.15f)
            ) {
                Text(
                    text = "${(session.accuracy * 100).toInt()}%",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = getAccuracyColor(session.accuracy).copy(alpha = 1f), // Ensure text is visible
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
            Icons.Default.BarChart,
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
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
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
            textAlign = TextAlign.Center
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

// Data classes for UI state
data class SessionHistoryItem(
    val sessionId: String,
    val formattedDate: String,
    val totalGuesses: Int,
    val correctGuesses: Int,
    val accuracy: Float
)

// Helper functions for semantic colors
private fun getErpiScoreColor(score: Float): Color {
    return when {
        score > 0.0f -> CueSightColors.Green  // Positive learning trajectory
        score > -0.1f -> CueSightColors.Orange  // Neutral/slight decline
        else -> CueSightColors.Red  // Negative trajectory
    }
}

private fun getAccuracyColor(accuracy: Float): Color {
    return when {
        accuracy >= 0.8f -> CueSightColors.Green  // High accuracy
        accuracy >= 0.6f -> CueSightColors.Orange  // Moderate accuracy
        else -> CueSightColors.Red  // Low accuracy
    }
}

private fun getCwaScoreColor(cwaScore: Float): Color {
    return when {
        cwaScore >= 0.7f -> CueSightColors.Green  // High CWA
        cwaScore >= 0.5f -> CueSightColors.Orange  // Moderate CWA
        else -> CueSightColors.Red  // Low CWA
    }
}

@Composable
fun ConfusionMatrixHeatmap(
    confusionMatrix: Map<String, Map<String, Int>>,
    emotions: List<String>,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // Wrapper for specialized matrix grid
        // Using simple Canvas drawing for Heatmap as Vico doesn't support heatmaps natively yet

        // Header Row
        Row {
            Spacer(modifier = Modifier.width(60.dp)) // Corner space
            emotions.forEach { emotion ->
                Text(
                    text = emotion.take(3),
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1
                )
            }
        }

        emotions.forEach { actualEmotion ->
            Row(modifier = Modifier.height(40.dp), verticalAlignment = Alignment.CenterVertically) {
                // Row Label
                Text(
                    text = actualEmotion.take(3),
                    modifier = Modifier.width(60.dp),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )

                // Cells
                val rowData = confusionMatrix[actualEmotion] ?: emptyMap()
                emotions.forEach { predictedEmotion ->
                    val count = rowData[predictedEmotion] ?: 0
                    val total = rowData.values.sum().toFloat().coerceAtLeast(1f)
                    val intensity = (count / total)

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .padding(1.dp)
                            .background(
                                MaterialTheme.colorScheme.primary.copy(alpha = intensity * 0.8f + 0.1f),
                                shape = MaterialTheme.shapes.extraSmall
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (count > 0) {
                            Text(
                                text = count.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (intensity > 0.5f) Color.White else Color.Black
                            )
                        }
                    }
                }
            }
        }
    }
}
