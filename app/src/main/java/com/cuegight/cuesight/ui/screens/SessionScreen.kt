package com.cuegight.cuesight.ui.screens

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.cuegight.cuesight.data.model.FrameQuality
import com.cuegight.cuesight.data.model.SessionMode
import com.cuegight.cuesight.data.model.SessionStatus
import com.cuegight.cuesight.viewmodel.SessionViewModel
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionScreen(
    studentId: Long,
    mode: String,
    onNavigateBack: () -> Unit,
    viewModel: SessionViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    var showEndDialog by remember { mutableStateOf(false) }
    var showGuessDialog by remember { mutableStateOf(false) }
    var selectedGuess by remember { mutableStateOf("Happy") }
    val emotionOptions = remember {
        listOf("Happy", "Sad", "Angry", "Surprise", "Neutral")
    }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    BackHandler(enabled = true) {
        showEndDialog = true
    }
    
    val sessionMode = try {
        SessionMode.valueOf(mode)
    } catch (e: Exception) {
        SessionMode.PRACTICE
    }
    
    LaunchedEffect(Unit) {
        if (!state.isStreaming && !state.isConnecting) {
            viewModel.startStreaming()
        }
    }

    LaunchedEffect(state.toastMessage) {
        state.toastMessage?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            viewModel.clearToast()
        }
    }

    LaunchedEffect(state.shouldNavigateToReport) {
        if (state.shouldNavigateToReport) {
            viewModel.onNavigationHandled()
            onNavigateBack()
        }
    }

    LaunchedEffect(state.shouldNavigateBack) {
        if (state.shouldNavigateBack) {
            viewModel.onNavigationHandled()
            onNavigateBack()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> viewModel.onAppBackgrounded(context)
                Lifecycle.Event.ON_RESUME -> viewModel.onAppForegrounded(context)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    if (showGuessDialog) {
        AlertDialog(
            onDismissRequest = { showGuessDialog = false },
            title = { Text("Log Student Guess") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    emotionOptions.forEach { option ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            RadioButton(
                                selected = selectedGuess == option,
                                onClick = { selectedGuess = option }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(option)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.logStudentGuess(selectedGuess)
                        showGuessDialog = false
                    },
                    enabled = state.canSubmitFeedback
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showGuessDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    if (showEndDialog) {
        AlertDialog(
            onDismissRequest = { showEndDialog = false },
            title = { Text("End Session") },
            text = { Text("Are you sure you want to end this training session?") },
            confirmButton = {
                Button(onClick = {
                    viewModel.endSession(SessionStatus.COMPLETED)
                    showEndDialog = false
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

    if (state.connectionLost) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text(state.connectionLostTitle) },
            text = { Text(state.connectionLostMessage) },
            confirmButton = {
                Button(onClick = { viewModel.retryConnection() }) {
                    Text("Retry")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.endSession(SessionStatus.INTERRUPTED, "Connection lost")
                }) {
                    Text("End Session")
                }
            }
        )
    }

    if (state.error.startsWith("Initialization Error")) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Initialization Error") },
            text = { Text("Emotion recognition model not found. Please reinstall the app.") },
            confirmButton = {
                Button(onClick = { (context as? Activity)?.finish() }) {
                    Text("Exit")
                }
            }
        )
    }

    state.hardwareError?.let { message ->
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Hardware Error") },
            text = { Text(message) },
            confirmButton = {
                Button(onClick = { viewModel.acknowledgeHardwareError() }) {
                    Text("OK")
                }
            }
        )
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text(if (sessionMode == SessionMode.TEACHING) "Teaching Mode" else "Practice Mode")
                        Text(
                            "Frames: ${state.frameCount}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { showEndDialog = true }) {
                        Icon(Icons.Default.Close, "End Session")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (sessionMode == SessionMode.TEACHING) 
                        MaterialTheme.colorScheme.primaryContainer 
                    else 
                        MaterialTheme.colorScheme.secondaryContainer
                )
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
            // Mode Description
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (sessionMode == SessionMode.TEACHING)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (sessionMode == SessionMode.TEACHING) Icons.Default.School else Icons.Default.Psychology,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = if (sessionMode == SessionMode.TEACHING)
                                "Demonstrate emotions for the student to observe and learn"
                            else
                                "Student practices expressing different emotions",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            if (state.isProcessingPaused) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.PauseCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                "Frame processing paused while app is in background",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            
            // Device Status Section
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Wifi,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Device Status",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = state.connectionStatus.ifEmpty { "Connecting..." },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        state.discoveredDevice?.let { device ->
                            Text(
                                text = "ESP32: ${device.ipAddress}:${device.webSocketPort}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                        }
                        if (state.isConnecting) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }

            if (state.warning.isNotEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                state.warning,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }
            
            // Emotion Display
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val emotionColor = if (state.emotionStale ||
                            (state.frameQuality == FrameQuality.POOR && sessionMode == SessionMode.TEACHING)
                        ) {
                            MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.5f)
                        } else {
                            MaterialTheme.colorScheme.onTertiaryContainer
                        }
                        Text(
                            "Detected Emotion:",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            state.detectedEmotion,
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = emotionColor
                        )
                        if (state.predictionDetail.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                state.predictionDetail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                            )
                        }
                        if (state.frameQuality == FrameQuality.NO_FACE || state.frameQuality == FrameQuality.POOR) {
                            Spacer(modifier = Modifier.height(8.dp))
                            AssistChip(
                                onClick = { },
                                label = {
                                    Text(
                                        if (state.frameQuality == FrameQuality.NO_FACE) {
                                            "No face detected"
                                        } else {
                                            "Uncertain"
                                        }
                                    )
                                },
                                enabled = false,
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                    disabledContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    disabledLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            )
                        }
                        if (state.emotionLogs.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Total Emotions Detected: ${state.emotionLogs.size}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
            
            // Error Display
            if (state.error.isNotEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Error,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                state.error,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
            
            // LED Control
            if (state.isStreaming && state.handshakeComplete) {
                item {
                    Text(
                        "Manual LED Control",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.sendLEDCommand("ON") },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.LightMode, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("LED ON")
                        }
                        
                        OutlinedButton(
                            onClick = { viewModel.sendLEDCommand("OFF") },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.LightMode, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("LED OFF")
                        }
                    }
                }
            }

            if (state.isStreaming && sessionMode == SessionMode.PRACTICE) {
                item {
                    Text(
                        "Practice Logging",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                selectedGuess = emotionOptions.first()
                                showGuessDialog = true
                            },
                            enabled = state.canSubmitFeedback,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Log Student Guess")
                        }
                    }
                }

                item {
                    OutlinedButton(
                        onClick = { viewModel.sendShowAnswer() },
                        enabled = state.canSubmitFeedback,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Visibility, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Show Answer")
                    }
                }
            }
            
            // Recent Emotions
            if (state.emotionLogs.isNotEmpty()) {
                item {
                    Text(
                        "Recent Emotions (Last 10)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                items(state.emotionLogs.takeLast(10).reversed()) { log ->
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(12.dp)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    log.emotion,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                log.studentGuess?.let { guess ->
                                    Text(
                                        text = "Guess: $guess • ${if (log.isCorrect == true) "Correct" else "Incorrect"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    )
                                }
                            }
                            Text(
                                java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                                    .format(java.util.Date(log.timestamp)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }
        }
    }
}
