package com.cuegight.cuesight.feature.teaching

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cuegight.cuesight.core.util.EmotionMapper
import com.cuegight.cuesight.ui.theme.CueSightColors
import org.koin.androidx.compose.koinViewModel

/**
 * New Teaching Screen - HTTP streaming with emotion detection
 * Clean, modern UI showing camera feed and detected emotions
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewTeachingScreen(
    studentId: Long,
    studentName: String,
    onNavigateBack: () -> Unit,
    viewModel: NewTeachingViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    var showEndDialog by remember { mutableStateOf(false) }

    // Start session
    LaunchedEffect(Unit) {
        viewModel.startSession(studentId, studentName)
        viewModel.updateSessionTime()
    }

    // Handle navigation
    LaunchedEffect(state.shouldNavigateBack) {
        if (state.shouldNavigateBack) {
            onNavigateBack()
            viewModel.onNavigated()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Teaching Mode", style = MaterialTheme.typography.titleLarge)
                        Text(
                            studentName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { showEndDialog = true }) {
                        Icon(Icons.Default.Close, "End session")
                    }
                },
                actions = {
                    if (!state.isPaused && state.isStreaming) {
                        IconButton(onClick = { viewModel.pauseSession() }) {
                            Icon(Icons.Default.Pause, "Pause")
                        }
                    } else if (state.isPaused) {
                        IconButton(onClick = { viewModel.resumeSession() }) {
                            Icon(Icons.Default.PlayArrow, "Resume")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Session timer
            SessionTimerCard(elapsedSeconds = state.sessionElapsedSeconds)

            // Camera feed
            CameraFeedCard(
                frame = state.currentFrame,
                isStreaming = state.isStreaming,
                isPaused = state.isPaused,
                error = state.streamError
            )

            // Current emotion display
            CurrentEmotionCard(
                emotion = state.currentEmotion,
                lastCommand = state.lastCommandSent
            )

            // Session stats
            SessionStatsRow(
                frameCount = state.frameCount,
                emotionCount = state.emotionCount,
                commandsSent = state.commandsSentCount
            )

            // Control buttons
            if (!state.isStreaming && !state.isPaused) {
                Button(
                    onClick = { viewModel.startStreaming() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.PlayArrow, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Start Streaming")
                }
            }

            if (state.isStreaming || state.isPaused) {
                Button(
                    onClick = { showEndDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.Stop, null)
                    Spacer(Modifier.width(8.dp))
                    Text("End Session")
                }
            }

            // Error display
            if (state.streamError != null) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Default.Error,
                            null,
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            state.streamError ?: "",
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }
    }

    // End session dialog
    if (showEndDialog) {
        AlertDialog(
            onDismissRequest = { showEndDialog = false },
            title = { Text("End Teaching Session?") },
            text = {
                Column {
                    Text("Session Summary:")
                    Text("Duration: ${formatDuration(state.sessionElapsedSeconds)}")
                    Text("Emotions detected: ${state.emotionCount}")
                    Text("Commands sent: ${state.commandsSentCount}")
                }
            },
            confirmButton = {
                Button(onClick = {
                    showEndDialog = false
                    viewModel.endSession()
                }) {
                    Text("End Session")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEndDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun SessionTimerCard(elapsedSeconds: Long) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Timer, null, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text = formatDuration(elapsedSeconds),
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun CameraFeedCard(
    frame: android.graphics.Bitmap?,
    isStreaming: Boolean,
    isPaused: Boolean,
    error: String?
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            when {
                frame != null -> {
                    androidx.compose.foundation.Image(
                        bitmap = frame.asImageBitmap(),
                        contentDescription = "Camera feed",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
                isPaused -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Pause,
                            null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text("Session Paused")
                    }
                }
                isStreaming -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator()
                        Text("Waiting for frames...")
                    }
                }
                else -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Videocam,
                            null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text("Ready to stream")
                    }
                }
            }
        }
    }
}

@Composable
fun CurrentEmotionCard(emotion: String?, lastCommand: String?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Current Emotion",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
            )

            AnimatedContent(
                targetState = emotion ?: "Waiting...",
                transitionSpec = {
                    fadeIn() + slideInVertically() togetherWith fadeOut()
                },
                label = "emotion"
            ) { targetEmotion ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (targetEmotion != "Waiting...") {
                        Text(
                            text = EmotionMapper.getEmotionEmoji(targetEmotion),
                            fontSize = 48.sp
                        )
                    }
                    Text(
                        text = targetEmotion,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (lastCommand != null) {
                Text(
                    text = "Last sent: ${EmotionMapper.getEmotionEmoji(lastCommand)} $lastCommand",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
fun SessionStatsRow(
    frameCount: Int,
    emotionCount: Int,
    commandsSent: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.Image,
            label = "Frames",
            value = frameCount.toString()
        )
        StatCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.EmojiEmotions,
            label = "Emotions",
            value = emotionCount.toString()
        )
        StatCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.Send,
            label = "Sent",
            value = commandsSent.toString()
        )
    }
}

@Composable
fun StatCard(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                icon,
                null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private fun formatDuration(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, secs)
    } else {
        "%d:%02d".format(minutes, secs)
    }
}
