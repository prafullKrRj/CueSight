package com.cuegight.cuesight

import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.net.InetSocketAddress
import java.net.Socket

class MainActivityTest : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                CameraScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen() {
    var ipAddress by remember { mutableStateOf("192.168.4.1") }
    var isStreaming by remember { mutableStateOf(false) }
    var fps by remember { mutableFloatStateOf(0f) }
    var statusText by remember { mutableStateOf("Tap Start to begin") }
    var frameSize by remember { mutableStateOf("") }
    var currentBitmap by remember {
        mutableStateOf<android.graphics.Bitmap?>(null)
    }

    LaunchedEffect(isStreaming, ipAddress) {
        if (!isStreaming) return@LaunchedEffect

        statusText = "Connecting..."
        fps = 0f

        withContext(Dispatchers.IO) {
            var retryCount = 0

            while (isActive && isStreaming) {
                var socket: Socket? = null
                try {
                    // ── Connect via raw TCP socket ──
                    socket = Socket()
                    socket.tcpNoDelay = true
                    socket.soTimeout = 5000
                    socket.connect(
                        InetSocketAddress(ipAddress, 81),
                        3000
                    )

                    retryCount = 0
                    statusText = "Streaming"

                    val input = DataInputStream(
                        BufferedInputStream(socket.getInputStream(), 32768)
                    )

                    var frameCount = 0
                    var fpsTimer = System.currentTimeMillis()

                    // ── Read frames continuously ──
                    while (isActive && isStreaming && !socket.isClosed) {
                        // Read 4-byte length header (big endian)
                        val len = input.readInt()

                        // Sanity check: JPEG frame shouldn't be > 100KB
                        if (len <= 0 || len > 100_000) {
                            statusText = "Bad frame size: $len"
                            break
                        }

                        // Read exactly 'len' bytes of JPEG data
                        val jpegBytes = ByteArray(len)
                        input.readFully(jpegBytes)

                        // Decode JPEG to Bitmap
                        val bmp = BitmapFactory.decodeByteArray(
                            jpegBytes, 0, jpegBytes.size
                        )
                        if (bmp != null) {
                            currentBitmap = bmp
                            frameCount++
                            frameSize = "${len / 1024.0}KB".take(6) + "KB"

                            val now = System.currentTimeMillis()
                            val elapsed = now - fpsTimer
                            if (elapsed >= 1000) {
                                fps = frameCount * 1000f / elapsed
                                frameCount = 0
                                fpsTimer = now
                                statusText = "Streaming ($frameSize/frame)"
                            }
                        }
                    }

                } catch (e: Exception) {
                    retryCount++
                    val backoff = (retryCount * 500L).coerceAtMost(3000L)
                    statusText = "Reconnecting (${retryCount})..."
                    delay(backoff)
                } finally {
                    try { socket?.close() } catch (_: Exception) {}
                }
            }
        }
    }

    // ─── UI ───
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ESP32-CAM Viewer") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            OutlinedTextField(
                value = ipAddress,
                onValueChange = { ipAddress = it },
                label = { Text("ESP32-CAM IP Address") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isStreaming
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    isStreaming = !isStreaming
                    if (!isStreaming) {
                        statusText = "Stopped"
                        fps = 0f
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = if (isStreaming)
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                else ButtonDefaults.buttonColors()
            ) {
                Text(
                    if (isStreaming) "⏹ Stop" else "▶ Start",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(statusText, style = MaterialTheme.typography.bodySmall)
                Text(
                    "%.1f FPS".format(fps),
                    style = MaterialTheme.typography.titleMedium,
                    color = when {
                        fps >= 6 -> MaterialTheme.colorScheme.primary
                        fps >= 3 -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.error
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    val bmp = currentBitmap
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Camera Feed",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Text("No image yet",
                            style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}