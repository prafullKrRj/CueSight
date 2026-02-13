package com.cuegight.cuesight.ui.screens.developer

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cuegight.cuesight.viewmodel.TestViewModel
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperTestScreen(
    onNavigateBack: () -> Unit,
    viewModel: TestViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    var showEndDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    BackHandler { showEndDialog = true }

    LaunchedEffect(Unit) { viewModel.startSession() }
    LaunchedEffect(state.toastMessage) {
        state.toastMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearToast()
        }
    }
    LaunchedEffect(state.shouldNavigateBack) {
        if (state.shouldNavigateBack) {
            viewModel.onNavigationHandled()
            onNavigateBack()
        }
    }

    if (showEndDialog) {
        AlertDialog(
            onDismissRequest = { showEndDialog = false },
            title = { Text("End Test Session") },
            text = { Text("Are you sure you want to end this test session?") },
            confirmButton = {
                Button(onClick = {
                    viewModel.endSession()
                    showEndDialog = false
                }) { Text("End Session") }
            },
            dismissButton = { TextButton(onClick = { showEndDialog = false }) { Text("Cancel") } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Developer Test Mode")
                        Text("Frames: ${state.frameCount}", style = MaterialTheme.typography.bodySmall)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { showEndDialog = true }) {
                        Icon(Icons.Default.Close, "End Session")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(colors = CardDefaults.cardColors(
                    containerColor = if (state.isConnected) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.errorContainer
                )) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (state.isConnected) Icons.Default.CheckCircle else Icons.Default.Cancel,
                            null,
                            tint = if (state.isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(if (state.isConnected) "Connected" else "Not Connected",
                                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("ESP32: ${state.ipAddress}:8888", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            item {
                Button(onClick = { viewModel.connect() }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Wifi, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Connect to ESP32")
                }
            }

            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                    Row(Modifier.padding(16.dp)) {
                        Icon(Icons.Default.BugReport, null, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("Temporary developer testing with full stream and live emotion detection")
                    }
                }
            }

            item {
                Card(Modifier.fillMaxWidth().height(400.dp)) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (state.currentFrame != null) {
                            Image(
                                state.currentFrame!!.asImageBitmap(),
                                "Camera Stream",
                                Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        } else {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Videocam, null, modifier = Modifier.size(64.dp))
                                Spacer(Modifier.height(16.dp))
                                Text("Press Start to begin streaming")
                            }
                        }
                    }
                }
            }

            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Detected Emotion:", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(8.dp))
                        Text(state.detectedEmotion, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                        if (state.predictionDetail.isNotEmpty()) {
                            Text(state.predictionDetail, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            if (state.error.isNotEmpty()) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Row(Modifier.padding(16.dp)) {
                            Icon(Icons.Default.Error, null)
                            Spacer(Modifier.width(12.dp))
                            Text(state.error)
                        }
                    }
                }
            }

            if (state.warning.isNotEmpty()) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                        Row(Modifier.padding(16.dp)) {
                            Icon(Icons.Default.Warning, null)
                            Spacer(Modifier.width(12.dp))
                            Text(state.warning)
                        }
                    }
                }
            }

            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { viewModel.startStreaming() },
                        enabled = !state.isStreaming && state.isConnected,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.PlayArrow, null)
                        Spacer(Modifier.width(4.dp))
                        Text("Start")
                    }
                    Button(
                        onClick = { viewModel.stopStreaming() },
                        enabled = state.isStreaming,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.Stop, null)
                        Spacer(Modifier.width(4.dp))
                        Text("Stop")
                    }
                }
            }

            if (state.isStreaming) {
                item {
                    Text("LED Control", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { viewModel.sendLEDCommand("ON") },
                            enabled = state.isConnected,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.LightMode, null)
                            Spacer(Modifier.width(4.dp))
                            Text("LED ON")
                        }
                        OutlinedButton(
                            onClick = { viewModel.sendLEDCommand("OFF") },
                            enabled = state.isConnected,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.LightMode, null)
                            Spacer(Modifier.width(4.dp))
                            Text("LED OFF")
                        }
                    }
                }
            }
        }
    }
}
