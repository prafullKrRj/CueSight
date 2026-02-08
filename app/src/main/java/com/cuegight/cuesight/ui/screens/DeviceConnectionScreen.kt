package com.cuegight.cuesight.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cuegight.cuesight.data.model.SessionMode
import com.cuegight.cuesight.viewmodel.SessionViewModel
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceConnectionScreen(
    studentId: Long,
    mode: String,
    onNavigateBack: () -> Unit,
    onConnected: () -> Unit,
    viewModel: SessionViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    val sessionMode = try {
        SessionMode.valueOf(mode)
    } catch (e: Exception) {
        SessionMode.PRACTICE
    }

    LaunchedEffect(Unit) {
        if (state.currentSession == null) {
            viewModel.startSession(studentId, sessionMode)
        }
        viewModel.startDiscovery()
    }

    LaunchedEffect(state.handshakeComplete) {
        if (state.handshakeComplete) {
            onConnected()
        }
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.stopDiscovery() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Connect to CueSight") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Navigate back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.Wifi,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Searching for ESP32-CAM...",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = state.connectionStatus.ifEmpty { "Scanning on UDP port 4210" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            state.discoveredDevice?.let { device ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Device Found",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text("IP: ${device.ipAddress}")
                        Text("WebSocket: ${device.webSocketPort}")
                    }
                }
            }

            Button(
                onClick = { viewModel.startStreaming() },
                enabled = state.discoveredDevice != null && !state.isConnecting,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Connect")
            }

            OutlinedButton(
                onClick = { viewModel.startDiscovery() },
                enabled = !state.isConnecting,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Rescan")
            }

            if (state.isConnecting) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
