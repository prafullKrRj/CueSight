package com.cuegight.cuesight.mock

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cuegight.cuesight.core.util.EmotionMapper
import com.cuegight.cuesight.ui.theme.CueSightColors
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MockTeachingScreen(
    studentName: String = "John Doe",
    onNavigateBack: () -> Unit = {},
    viewModel: MockTeachingViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    var showEndDialog by remember { mutableStateOf(false) }

    // Auto-start session for demo
    LaunchedEffect(Unit) {
        delay(500)
        viewModel.startSession()
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = studentName,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Teaching Mode",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { showEndDialog = true }) {
                        Icon(Icons.Rounded.Close, "End Session")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 1. Session Timer
            SessionTimerCard(durationSeconds = state.sessionDurationSeconds)

            // 2. Mock Camera Feed
            MockCameraFeedCard(
                currentEmotion = state.currentEmotion,
                confidence = state.confidence
            )

            // 3. Current Emotion Display
            AnimatedContent(
                targetState = state.currentEmotion,
                label = "emotion"
            ) { emotion ->
                EmotionDisplayCard(
                    emotion = emotion,
                    confidence = state.confidence
                )
            }

            // 4. Session Statistics
            SessionStatsCard(
                frameCount = state.frameCount,
                emotionsDetected = state.emotionsDetected,
                commandsSent = state.commandsSent
            )

            // 5. Control Buttons
            ControlButtonsRow(
                isStreaming = state.isStreaming,
                isPaused = state.isPaused,
                onPause = { viewModel.pauseSession() },
                onResume = { viewModel.resumeSession() },
                onEnd = { showEndDialog = true }
            )
        }
    }

    // End Session Dialog
    if (showEndDialog) {
        EndSessionDialog(
            onDismiss = { showEndDialog = false },
            onConfirm = {
                viewModel.endSession()
                onNavigateBack()
            },durationSeconds = state.sessionDurationSeconds,
            emotionsDetected = state.emotionsDetected
        )
    }
}

@Composable
private fun SessionTimerCard(durationSeconds: Long) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Rounded.Timer,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = formatDuration(durationSeconds),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun MockCameraFeedCard(
    currentEmotion: String,
    confidence: Float
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(4f / 3f)
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1A1A2E),
                        Color(0xFF16213E)
                    )
                )
            )
    ) {
        // Mock face detection box
        Box(
            modifier = Modifier
                .size(200.dp)
                .align(Alignment.Center)
                .background(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(12.dp)
                )
        ) {
            // Emotion label overlay
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(8.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
            ) {
//                Row(
//                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
//                    verticalAlignment = Alignment.CenterVertically,
//                    horizontalArrangement = Arrangement.spacedBy(6.dp)
//                ) {
//                    Text(
//                        text = EmotionMapper.getEmotionEmoji(currentEmotion),
//                        fontSize = 18.sp
//                    )
//                    Text(
//                        text = currentEmotion,
//                        color = Color.White,
//                        fontWeight = FontWeight.Bold,
//                        fontSize = 14.sp
//                    )
//                    Text(
//                        text = "${(confidence * 100).toInt()}%",
//                        color = Color.White.copy(alpha = 0.8f),
//                        fontSize = 12.sp
//                    )
//                }
            }
        }

        // "Live" indicator
        Surface(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp),
            shape = RoundedCornerShape(6.dp),
            color = Color.Red
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(Color.White, shape = androidx.compose.foundation.shape.CircleShape)
                )
                Text(
                    text = "LIVE",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun EmotionDisplayCard(
    emotion: String,
    confidence: Float
) {
    Surface(
        shape = RoundedCornerShape(100.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = EmotionMapper.getEmotionEmoji(emotion),
                fontSize = 36.sp
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    text = emotion.uppercase(),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${(confidence * 100).toInt()}% confident",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun SessionStatsCard(
    frameCount: Int,
    emotionsDetected: Int,
    commandsSent: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Session Statistics",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatItem("Frames", frameCount.toString())
                StatItem("Emotions", emotionsDetected.toString())
                StatItem("Commands", commandsSent.toString())
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun ControlButtonsRow(
    isStreaming: Boolean,
    isPaused: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onEnd: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (isStreaming && !isPaused) {
            Button(
                onClick = onPause,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                Icon(Icons.Rounded.Pause, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Pause")
            }
        } else if (isPaused) {
            Button(
                onClick = onResume,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Resume")
            }
        }
        
        Button(
            onClick = onEnd,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error
            )
        ) {
            Icon(Icons.Rounded.Stop, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("End Session")
        }
    }
}

@Composable
private fun EndSessionDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    durationSeconds: Long,
    emotionsDetected: Int
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "End Session?",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Session Summary:")
                Text("Duration: ${formatDuration(durationSeconds)}")
                Text("Emotions Detected: $emotionsDetected")
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("End Session")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Continue")
            }
        }
    )
}

private fun formatDuration(seconds: Long): String {
    val minutes = seconds / 60
    val secs = seconds % 60
    return String.format("%02d:%02d", minutes, secs)
}
