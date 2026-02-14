package com.cuegight.cuesight.ui.screens.teaching

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.cuegight.cuesight.data.model.SessionStatus
import com.cuegight.cuesight.viewmodel.TeachingViewModel
import org.koin.androidx.compose.koinViewModel

// Emotion color constants for consistent theming
private object EmotionColors {
    val Happy = Color(0xFFFDD835)      // Yellow - cheerful and bright
    val Sad = Color(0xFF42A5F5)        // Blue - calm and melancholy
    val Angry = Color(0xFFEF5350)      // Red - intense and attention-grabbing
    val Surprised = Color(0xFF66BB6A)  // Green - unexpected and fresh
    val Neutral = Color(0xFF9E9E9E)    // Gray - balanced and neutral
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeachingSessionScreen(
    studentId: Long,
    onNavigateBack: () -> Unit,
    viewModel: TeachingViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var showEndSessionDialog by remember { mutableStateOf(false) }
    var showIpDialog by remember { mutableStateOf(false) }
    var showConnectionLostDialog by remember { mutableStateOf(false) }
    var showHardwareErrorDialog by remember { mutableStateOf(false) }
    var isRecentEmotionsExpanded by remember { mutableStateOf(false) }

    // Start session on launch
    LaunchedEffect(Unit) {
        viewModel.startSession(studentId)
    }

    // Handle navigation back
    LaunchedEffect(state.shouldNavigateBack) {
        if (state.shouldNavigateBack) {
            viewModel.onNavigationHandled()
            onNavigateBack()
        }
    }

    // Handle toasts
    LaunchedEffect(state.toastMessage) {
        state.toastMessage?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            viewModel.clearToast()
        }
    }

    // Handle connection lost dialog
    LaunchedEffect(state.connectionLost) {
        showConnectionLostDialog = state.connectionLost
    }

    // Handle hardware error dialog
    LaunchedEffect(state.hardwareError) {
        showHardwareErrorDialog = state.hardwareError != null
    }

    // Lifecycle handling
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> viewModel.onAppBackgrounded(context)
                Lifecycle.Event.ON_RESUME -> viewModel.onAppForegrounded(context)
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Back handler
    BackHandler {
        showEndSessionDialog = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Teaching Session", style = MaterialTheme.typography.titleLarge)
                        Text(
                            formatTime(state.sessionElapsedSeconds),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { showEndSessionDialog = true }) {
                        Icon(Icons.Default.Close, "End session")
                    }
                },
                actions = {
                    // Pause/Play toggle
                    if (state.isStreaming || state.isPaused) {
                        Crossfade(targetState = state.isPaused, label = "pause_play") { paused ->
                            IconButton(
                                onClick = {
                                    if (paused) viewModel.resumeSession()
                                    else viewModel.pauseSession()
                                }
                            ) {
                                Icon(
                                    if (paused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                    if (paused) "Resume" else "Pause"
                                )
                            }
                        }
                    }
                    // Settings
                    IconButton(onClick = { showIpDialog = true }) {
                        Icon(Icons.Default.Settings, "Settings")
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
            // Connection Banner (only when not connected)
            if (!state.isConnected) {
                ConnectionBanner(
                    isConnecting = state.isReconnecting,
                    onConnect = { viewModel.connect() }
                )
            }

            // Camera Feed Card (HERO ELEMENT)
            CameraFeedCard(
                state = state
            )

            // Detected Emotion Display
            EmotionDisplayCard(
                emotion = state.detectedEmotion,
                confidence = state.emotionConfidence,
                frameQuality = state.frameQuality,
                emotionStale = state.emotionStale
            )

            // Live Session Stats
            LiveStatsRow(
                emotionCount = state.emotionLogs.size,
                activeSeconds = state.activeStreamingSeconds,
                pauseCount = state.pauseCount
            )

            // Control Buttons
            ControlButtonsSection(
                state = state,
                onConnect = { viewModel.connect() },
                onStartStreaming = { viewModel.startStreaming() },
                onPause = { viewModel.pauseSession() },
                onResume = { viewModel.resumeSession() },
                onEndSession = { showEndSessionDialog = true }
            )

            // Recent Emotions Log (Collapsible)
            RecentEmotionsSection(
                logs = state.emotionLogs,
                isExpanded = isRecentEmotionsExpanded,
                onToggle = { isRecentEmotionsExpanded = !isRecentEmotionsExpanded }
            )

            // Emotion Frequency Bar
            EmotionFrequencyBar(
                frequencyMap = state.emotionFrequencyMap
            )
        }
    }

    // Dialogs
    if (showEndSessionDialog) {
        EndSessionDialog(
            durationSeconds = state.sessionElapsedSeconds,
            emotionCount = state.emotionLogs.size,
            onDismiss = { showEndSessionDialog = false },
            onConfirm = {
                showEndSessionDialog = false
                viewModel.endSession(SessionStatus.COMPLETED)
            }
        )
    }

    if (showIpDialog) {
        IpAddressDialog(
            currentIp = state.ipAddress,
            onDismiss = { showIpDialog = false },
            onSave = { newIp ->
                viewModel.setIpAddress(newIp)
                showIpDialog = false
                viewModel.connect()
            }
        )
    }

    if (showConnectionLostDialog) {
        ConnectionLostDialog(
            title = state.connectionLostTitle,
            message = state.connectionLostMessage,
            onEndSession = {
                showConnectionLostDialog = false
                viewModel.endSession(SessionStatus.INTERRUPTED, "Connection lost")
            },
            onRetry = {
                showConnectionLostDialog = false
                viewModel.retryConnection()
            }
        )
    }

    if (showHardwareErrorDialog) {
        HardwareErrorDialog(
            message = state.hardwareError ?: "",
            onDismiss = {
                showHardwareErrorDialog = false
                viewModel.acknowledgeHardwareError()
                viewModel.endSession(SessionStatus.INTERRUPTED, "Hardware error")
            }
        )
    }
}

@Composable
fun ConnectionBanner(
    isConnecting: Boolean,
    onConnect: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                "Not connected to CueSight glasses",
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodyMedium
            )
            if (isConnecting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Button(onClick = onConnect) {
                    Text("Connect")
                }
            }
        }
    }
}

@Composable
fun CameraFeedCard(
    state: com.cuegight.cuesight.viewmodel.TeachingState
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(380.dp),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            when {
                state.currentFrame != null -> {
                    androidx.compose.foundation.Image(
                        bitmap = state.currentFrame.asImageBitmap(),
                        contentDescription = "Camera feed",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
                state.isStreaming && state.currentFrame == null -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator()
                        Text("Starting stream...")
                    }
                }
                !state.isStreaming && !state.isPaused -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Videocam,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                        Text(
                            "Tap Start to begin",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            // Pause Overlay
            AnimatedVisibility(
                visible = state.isPaused,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Pause,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = Color.White
                        )
                        Text(
                            "Session Paused",
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Tap Resume to continue",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EmotionDisplayCard(
    emotion: String,
    confidence: Float,
    frameQuality: com.cuegight.cuesight.data.model.FrameQuality,
    emotionStale: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Currently Detecting:",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
            )

            AnimatedContent(
                targetState = emotion,
                transitionSpec = {
                    fadeIn() + slideInVertically() togetherWith fadeOut()
                },
                label = "emotion_text"
            ) { targetEmotion ->
                Text(
                    targetEmotion,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(
                        alpha = if (emotionStale) 0.5f else 1f
                    )
                )
            }

            // Confidence bar
            val animatedConfidence by animateFloatAsState(
                targetValue = confidence,
                label = "confidence"
            )
            LinearProgressIndicator(
                progress = { animatedConfidence },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
            )

            if (frameQuality == com.cuegight.cuesight.data.model.FrameQuality.NO_FACE) {
                AssistChip(
                    onClick = { },
                    label = { Text("No face detected") }
                )
            }
        }
    }
}

@Composable
fun LiveStatsRow(
    emotionCount: Int,
    activeSeconds: Long,
    pauseCount: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.BarChart,
            label = "Emotions",
            value = emotionCount.toString()
        )
        StatCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.Timer,
            label = "Active",
            value = formatTime(activeSeconds)
        )
        StatCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Default.Pause,
            label = "Pauses",
            value = pauseCount.toString()
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
                contentDescription = null,
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

@Composable
fun ControlButtonsSection(
    state: com.cuegight.cuesight.viewmodel.TeachingState,
    onConnect: () -> Unit,
    onStartStreaming: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onEndSession: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        when {
            !state.isConnected && !state.isStreaming -> {
                Button(
                    onClick = onConnect,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Connect & Start")
                }
            }
            state.isConnected && !state.isStreaming && !state.isPaused -> {
                Button(
                    onClick = onStartStreaming,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.PlayArrow, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Start Streaming")
                }
            }
            state.isConnected && state.isStreaming && !state.isPaused -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onPause,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Pause, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Pause")
                    }
                    Button(
                        onClick = onEndSession,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Default.Stop, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("End")
                    }
                }
            }
            state.isPaused -> {
                Button(
                    onClick = onResume,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Default.PlayArrow, null, Modifier.size(24.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Resume Session", style = MaterialTheme.typography.titleMedium)
                }
                TextButton(
                    onClick = onEndSession,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "End Session",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
fun RecentEmotionsSection(
    logs: List<com.cuegight.cuesight.data.model.EmotionLog>,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onToggle
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Recent Activity",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Icon(
                    if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand"
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val recentLogs = logs.takeLast(10).reversed()
                    if (recentLogs.isEmpty()) {
                        Text(
                            "No emotions detected yet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        recentLogs.forEach { log ->
                            EmotionLogRow(log)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmotionLogRow(log: com.cuegight.cuesight.data.model.EmotionLog) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            getEmotionEmoji(log.emotion),
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            log.emotion,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            formatTimestamp(log.timestamp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}

@Composable
fun EmotionFrequencyBar(
    frequencyMap: Map<String, Int>
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Emotion Distribution",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            if (frequencyMap.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(4.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No data yet",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val total = frequencyMap.values.sum().toFloat()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp)
                        .clip(RoundedCornerShape(4.dp))
                ) {
                    frequencyMap.forEach { (emotion, count) ->
                        val weight = count / total
                        Box(
                            modifier = Modifier
                                .weight(weight)
                                .fillMaxHeight()
                                .background(getEmotionColor(emotion))
                        )
                    }
                }

                // Legend
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    frequencyMap.forEach { (emotion, count) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(getEmotionColor(emotion), RoundedCornerShape(2.dp))
                            )
                            Text(
                                "$emotion ($count)",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EndSessionDialog(
    durationSeconds: Long,
    emotionCount: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("End Teaching Session?") },
        text = {
            Text("Session duration: ${formatTime(durationSeconds)}. $emotionCount emotions detected. This session will be saved.")
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("End Session")
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
fun IpAddressDialog(
    currentIp: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var ipText by remember { mutableStateOf(currentIp) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ESP32 Connection") },
        text = {
            OutlinedTextField(
                value = ipText,
                onValueChange = { ipText = it },
                label = { Text("IP Address") },
                singleLine = true
            )
        },
        confirmButton = {
            Button(onClick = { onSave(ipText) }) {
                Text("Save & Reconnect")
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
fun ConnectionLostDialog(
    title: String,
    message: String,
    onEndSession: () -> Unit,
    onRetry: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { },
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            Button(onClick = onRetry) {
                Text("Retry")
            }
        },
        dismissButton = {
            TextButton(onClick = onEndSession) {
                Text("End Session")
            }
        }
    )
}

@Composable
fun HardwareErrorDialog(
    message: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { },
        title = { Text("Hardware Error") },
        text = { Text(message) },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("OK")
            }
        }
    )
}

// Helper functions
private fun formatTime(seconds: Long): String {
    val minutes = seconds / 60
    val secs = seconds % 60
    return "%d:%02d".format(minutes, secs)
}

private fun formatTimestamp(timestamp: Long): String {
    val date = java.util.Date(timestamp)
    val format = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
    return format.format(date)
}

private fun getEmotionEmoji(emotion: String): String {
    return when (emotion) {
        "Happy" -> "😊"
        "Sad" -> "😢"
        "Angry" -> "😠"
        "Surprised" -> "😲"
        "Neutral" -> "😐"
        else -> "❓"
    }
}

@Composable
private fun getEmotionColor(emotion: String): Color {
    return when (emotion) {
        "Happy" -> EmotionColors.Happy
        "Sad" -> EmotionColors.Sad
        "Angry" -> EmotionColors.Angry
        "Surprised" -> EmotionColors.Surprised
        "Neutral" -> EmotionColors.Neutral
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
}
