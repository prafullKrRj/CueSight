package com.cuegight.cuesight.feature.teaching

import android.graphics.Bitmap
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VideocamOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.cuegight.cuesight.core.util.EmotionMapper
import com.cuegight.cuesight.ui.theme.SkyBluePalette
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel

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
    var webView by remember { mutableStateOf<WebView?>(null) }

    // Start session
    LaunchedEffect(Unit) {
        viewModel.startSession(studentId, studentName)
    }

    // Handle navigation
    LaunchedEffect(state.shouldNavigateBack) {
        if (state.shouldNavigateBack) {
            onNavigateBack()
            viewModel.onNavigated()
        }
    }

    // Capture WebView screenshots
    LaunchedEffect(state.isStreaming, webView) {
        if (state.isStreaming && webView != null) {
            while (state.isStreaming && !state.isPaused) {
                try {
                    val bitmap = captureWebViewBitmap(webView!!)
                    if (bitmap != null) {
                        viewModel.onFrameReceived(bitmap)
                    }
                } catch (e: Exception) {
                    // Ignore capture errors
                }
                delay(500)
            }
        }
    }

    Scaffold(
        containerColor = SkyBluePalette.Sky50,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            studentName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (state.isStreaming && state.frameCount > 0) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Canvas(modifier = Modifier.size(6.dp)) { drawCircle(color = SkyBluePalette.SuccessBlue) }
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    "LIVE",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SkyBluePalette.SuccessBlue,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (state.isPaused) viewModel.resumeSession() else viewModel.pauseSession()
                    }) {
                        Icon(
                            imageVector = if (state.isPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                            contentDescription = "Toggle Pause",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showEndDialog = true },
                        colors = IconButtonDefaults.iconButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Rounded.PowerSettingsNew, "End Session")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Brush.verticalGradient(listOf(SkyBluePalette.Sky100, SkyBluePalette.Sky50)))
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp), // Slightly reduced padding to give more room for video
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                shape = RoundedCornerShape(18.dp),
                color = SkyBluePalette.Sky200.copy(alpha = 0.45f)
            ) {
                Text(
                    text = "Live feed + emotion detection. Use this mode to model clear expressions for learners.",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = SkyBluePalette.Sky700
                )
            }

            // 1. Timer
            Text(
                text = formatDuration(state.sessionElapsedSeconds),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Light,
                color = MaterialTheme.colorScheme.primary
            )

            // 2. Camera Feed - FULL FRAME (4:3 Aspect Ratio)
            // OV2640 standard is 4:3. We force this ratio to avoid black bars or cropping.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f) // Standard ESP32-CAM Aspect Ratio (e.g. 800x600)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                WebViewCameraFeedContent(
                    state = state,
                    isStreaming = state.isStreaming,
                    isPaused = state.isPaused,
                    onWebViewCreated = { webView = it }
                )
            }

            // 3. Current Emotion
            AnimatedContent(
                targetState = state.currentEmotion ?: "Neutral",
                label = "emotion"
            ) { targetEmotion ->
                Surface(
                    shape = RoundedCornerShape(100.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.height(64.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = EmotionMapper.getEmotionEmoji(targetEmotion),
                            fontSize = 28.sp
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = targetEmotion,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            // 4. Stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                MinimalStatItem("Frames", state.frameCount.toString())
                MinimalStatItem("Emotions", state.emotionCount.toString())
                MinimalStatItem("Sent", state.commandsSentCount.toString())
            }

            Spacer(Modifier.weight(1f))

            // 5. Controls
            Box(modifier = Modifier.padding(bottom = 24.dp)) {
                if (!state.isStreaming && !state.isPaused) {
                    Button(
                        onClick = { viewModel.startStreaming() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.Rounded.Videocam, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Start Streaming", fontSize = 16.sp)
                    }
                } else if (state.isStreaming && state.frameCount > 0) {
                    OutlinedButton(
                        onClick = { viewModel.stopStreaming() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Rounded.Stop, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Stop Stream")
                    }
                }
            }

            if (state.streamError != null) {
                Text(
                    text = state.streamError ?: "",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
        }
    }

    if (showEndDialog) {
        AlertDialog(
            onDismissRequest = { showEndDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = {
                Text(
                    "End Session?",
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Text(
                    "This will save session data and return to the dashboard.",
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showEndDialog = false
                        viewModel.endSession()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("End") }
            },
            dismissButton = {
                TextButton(onClick = { showEndDialog = false }) { Text("Cancel") }
            }
        )
    }
}

// --- Helper Composables ---

@Composable
fun MinimalStatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 1.sp
        )
    }
}

@Composable
fun WebViewCameraFeedContent(
    state: TeachingState,
    isStreaming: Boolean,
    isPaused: Boolean,
    onWebViewCreated: (WebView) -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        when {
            isStreaming && !isPaused -> {
                if (state.useJpegStream) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { context ->
                            WebView(context).apply {
                                settings.javaScriptEnabled = true
                                settings.loadWithOverviewMode = true
                                settings.useWideViewPort =
                                    true // CRITICAL for fitting frame to width
                                settings.cacheMode = WebSettings.LOAD_NO_CACHE
                                settings.builtInZoomControls = false
                                settings.displayZoomControls = false
                                // We are setting initial scale to 1 to prevent zooming in
                                setInitialScale(1)
                                loadUrl("http://192.168.4.1/stream")
                                onWebViewCreated(this)
                            }
                        }
                    )
                } else {
                    if (state.currentFrame != null) {
                        Image(
                            bitmap = state.currentFrame.asImageBitmap(),
                            contentDescription = "Feed",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.FillBounds // Fill the 4:3 box completely
                        )
                    } else {
                        CircularProgressIndicator(strokeWidth = 2.dp)
                    }
                }
            }

            isPaused -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Rounded.PauseCircle,
                        null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Paused", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            else -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Rounded.VideocamOff,
                        null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Stream Offline", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private fun captureWebViewBitmap(webView: WebView): Bitmap? {
    return try {
        val bitmap = Bitmap.createBitmap(
            webView.width,
            webView.height,
            Bitmap.Config.ARGB_8888
        )
        val canvas = android.graphics.Canvas(bitmap)
        webView.draw(canvas)
        bitmap
    } catch (e: Exception) {
        null
    }
}

private fun formatDuration(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, secs) else "%02d:%02d".format(
        minutes,
        secs
    )
}