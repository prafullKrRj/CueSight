package com.cuegight.cuesight.feature.practice.ui

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
import com.cuegight.cuesight.feature.practice.domain.model.*
import com.cuegight.cuesight.feature.practice.ui.components.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsDashboardScreen(
    onNavigateBack: () -> Unit,
    viewModel: PracticeModeViewModel
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showWeightDialog by remember { mutableStateOf(false) }
    var erpiResult by remember { mutableStateOf<ErpiResult?>(null) }
    var masteryResult by remember { mutableStateOf<MasteryResult?>(null) }
    var cwaResult by remember { mutableStateOf<CwaResult?>(null) }

    LaunchedEffect(Unit) {
        viewModel.getErpiResult().collect { erpiResult = it }
    }

    LaunchedEffect(Unit) {
        viewModel.getMasteryResult().collect { masteryResult = it }
    }

    LaunchedEffect(Unit) {
        viewModel.getCwaResult().collect { cwaResult = it }
    }

    if (showWeightDialog) {
        WeightAdjustmentDialog(
            currentWeights = cwaResult?.weights ?: emptyMap(),
            onDismiss = { showWeightDialog = false },
            onSave = { weights ->
                scope.launch {
                    val success = viewModel.updateTherapistWeights(weights)
                    if (success) {
                        Toast.makeText(context, "Weights updated", Toast.LENGTH_SHORT).show()
                        showWeightDialog = false
                    } else {
                        Toast.makeText(context, "Weights must sum to 1.0", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Analytics Dashboard") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        scope.launch {
                            val uri = viewModel.exportSessionsToCsv(context)
                            if (uri != null) {
                                Toast.makeText(context, "Exported to Downloads", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "Export failed", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }) {
                        Icon(Icons.Default.Download, "Export CSV")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Card 1: Learning Trajectory (ERPI)
            item {
                ErpiCard(erpiResult)
            }

            // Card 2: Emotion Mastery
            item {
                MasteryCard(masteryResult)
            }

            // Card 3: Confusion Matrix
            item {
                ConfusionMatrixCard(cwaResult)
            }

            // Card 4: Confusion-Weighted Accuracy
            item {
                CwaCard(
                    result = cwaResult,
                    onAdjustWeights = { showWeightDialog = true }
                )
            }
        }
    }
}

@Composable
fun ErpiCard(result: ErpiResult?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
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

            result?.let {
                Text(
                    "ERPI Score: %.3f".format(it.erpiScore),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        it.erpiScore > 0.0f -> Color(0xFF4CAF50)
                        it.erpiScore > -0.1f -> Color(0xFFFF9800)
                        else -> Color(0xFFF44336)
                    }
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    it.interpretation,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Slope: %.4f".format(it.slope),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            } ?: Text("Computing...", style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
fun MasteryCard(result: MasteryResult?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
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

            result?.let {
                Text(
                    "Overall Mastery: ${(it.aggregateMastery * 100).toInt()}%",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(16.dp))

                MasteryBars(it.perEmotionScores)

                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Strongest:", style = MaterialTheme.typography.bodySmall)
                        Text(it.strongestEmotion, fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Weakest:", style = MaterialTheme.typography.bodySmall)
                        Text(it.weakestEmotion, fontWeight = FontWeight.Bold, color = Color(0xFFF44336))
                    }
                }
            } ?: Text("Computing...", style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
fun ConfusionMatrixCard(result: CwaResult?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
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

            result?.let {
                ConfusionMatrixGrid(
                    matrix = it.confusionMatrix,
                    emotions = it.confusionMatrix.keys.sorted()
                )
            } ?: Text("Computing...", style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
fun CwaCard(result: CwaResult?, onAdjustWeights: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Confusion-Weighted Accuracy",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(16.dp))

            result?.let {
                Text(
                    "${(it.cwaScore * 100).toInt()}%",
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(Modifier.height(16.dp))

                Button(onClick = onAdjustWeights) {
                    Icon(Icons.Default.Settings, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Adjust Weights")
                }
            } ?: CircularProgressIndicator()
        }
    }
}
