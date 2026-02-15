package com.cuegight.cuesight.feature.connection

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cuegight.cuesight.ui.theme.CueSightColors
import org.koin.androidx.compose.koinViewModel

/**
 * Multi-step connection flow UI
 * Shows progress through WiFi and HTTP connection setup
 */
@Composable
fun ConnectionScreen(
    onNavigateToHome: () -> Unit,
    viewModel: ConnectionViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()

    // Handle navigation
    LaunchedEffect(state.shouldNavigateToHome) {
        if (state.shouldNavigateToHome) {
            onNavigateToHome()
            viewModel.onNavigated()
        }
    }

    // Start checking WiFi status
    LaunchedEffect(Unit) {
        viewModel.checkWifiStatus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Title
            Text(
                text = "Connect to CueSight",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            // Progress indicators
            ProgressIndicators(currentStep = state.currentStep)

            // Main content area
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    when (state.currentStep) {
                        ConnectionStep.CHECK_WIFI -> CheckWiFiStep(
                            isWifiEnabled = state.isWifiEnabled,
                            isConnectedToESP32 = state.isConnectedToESP32,
                            isLoading = state.isLoading,
                            onCheckWifi = { viewModel.checkWifiStatus() }
                        )
                        ConnectionStep.CONNECT_WIFI -> ConnectWiFiStep(
                            isLoading = state.isLoading,
                            needsUserAction = state.needsUserAction,
                            userActionMessage = state.userActionMessage,
                            error = state.error,
                            onConnect = { viewModel.connectToWiFi() },
                            onOpenSettings = { viewModel.openWifiSettings() },
                            onRetryAfterSettings = { viewModel.retryAfterSettings() }
                        )
                        ConnectionStep.TEST_HTTP -> TestHttpStep(
                            isLoading = state.isLoading,
                            error = state.error,
                            onTest = { viewModel.testHttpConnection() }
                        )
                        ConnectionStep.SUCCESS -> SuccessStep()
                    }
                }
            }

            // Error message
            if (state.error != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = state.error ?: "",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                Button(
                    onClick = { viewModel.retry() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Refresh, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Retry")
                }
            }
        }
    }
}

@Composable
fun ProgressIndicators(currentStep: ConnectionStep) {
    val steps = listOf(
        ConnectionStep.CHECK_WIFI,
        ConnectionStep.CONNECT_WIFI,
        ConnectionStep.TEST_HTTP,
        ConnectionStep.SUCCESS
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        steps.forEachIndexed { index, step ->
            val isActive = currentStep.ordinal >= step.ordinal
            val isCurrentStep = currentStep == step

            AnimatedContent(
                targetState = isActive,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "step_$index"
            ) { active ->
                Box(
                    modifier = Modifier
                        .size(if (isCurrentStep) 16.dp else 12.dp)
                        .clip(CircleShape)
                        .background(
                            if (active) CueSightColors.Purple
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                )
            }

            if (index < steps.size - 1) {
                Box(
                    modifier = Modifier
                        .width(32.dp)
                        .height(2.dp)
                        .background(
                            if (currentStep.ordinal > index) CueSightColors.Purple
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                )
            }
        }
    }
}

@Composable
fun CheckWiFiStep(
    isWifiEnabled: Boolean,
    isConnectedToESP32: Boolean,
    isLoading: Boolean,
    onCheckWifi: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            Icons.Default.Wifi,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = if (isWifiEnabled) CueSightColors.Green else MaterialTheme.colorScheme.onSurface
        )

        Text(
            text = "Checking WiFi Status",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        if (isLoading) {
            CircularProgressIndicator()
        } else {
            if (isConnectedToESP32) {
                Text(
                    text = "✓ Connected to ESP32_CAM",
                    color = CueSightColors.Green,
                    style = MaterialTheme.typography.bodyLarge
                )
            } else if (isWifiEnabled) {
                Text(
                    text = "WiFi is enabled but not connected to ESP32_CAM",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                Text(
                    text = "WiFi is disabled. Please enable WiFi to continue.",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
fun ConnectWiFiStep(
    isLoading: Boolean,
    needsUserAction: Boolean,
    userActionMessage: String?,
    error: String?,
    onConnect: () -> Unit,
    onOpenSettings: () -> Unit,
    onRetryAfterSettings: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            Icons.Default.Router,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = CueSightColors.Blue
        )

        Text(
            text = "Connect to ESP32_CAM",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        if (needsUserAction) {
            Text(
                text = userActionMessage ?: "Manual action required",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium
            )

            Button(
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Open WiFi Settings")
            }

            OutlinedButton(
                onClick = onRetryAfterSettings,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("I've Connected")
            }
        } else {
            Text(
                text = "SSID: ESP32_CAM\nPassword: 12345678",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium
            )

            if (isLoading) {
                CircularProgressIndicator()
                Text("Connecting...", style = MaterialTheme.typography.bodySmall)
            } else {
                Button(
                    onClick = onConnect,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Connect")
                }
            }
        }
    }
}

@Composable
fun TestHttpStep(
    isLoading: Boolean,
    error: String?,
    onTest: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            Icons.Default.CloudSync,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = CueSightColors.Orange
        )

        Text(
            text = "Testing Connection",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        if (isLoading) {
            CircularProgressIndicator()
            Text("Connecting to camera...", style = MaterialTheme.typography.bodyMedium)
        } else {
            Text(
                text = "Testing HTTP connection to ESP32 camera",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium
            )

            Button(
                onClick = onTest,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Test Connection")
            }
        }
    }
}

@Composable
fun SuccessStep() {
    val infiniteTransition = rememberInfiniteTransition(label = "success_pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            modifier = Modifier
                .size(80.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale },
            tint = CueSightColors.Green
        )

        Text(
            text = "Connected Successfully!",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = CueSightColors.Green
        )

        Text(
            text = "Preparing your workspace...",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}
