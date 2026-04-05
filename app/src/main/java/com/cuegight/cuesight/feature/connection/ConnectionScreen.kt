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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cuegight.cuesight.ui.theme.SkyBluePalette
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
        viewModel.checkConnection()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(SkyBluePalette.Sky100, SkyBluePalette.Sky50))),
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
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = SkyBluePalette.Sky50.copy(alpha = 0.96f))
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
                            isLoading = state.isLoading,
                            onCheckWifi = { viewModel.checkConnection() }
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
            if (state.error != null && state.currentStep != ConnectionStep.SUCCESS) {
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
            
            // Skip connection button - always show (except on success)
            if (state.currentStep != ConnectionStep.SUCCESS) {
                TextButton(
                    onClick = { viewModel.skipConnection() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Continue without connection (Analytics only)",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
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
                            if (active) SkyBluePalette.Sky700
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
                            if (currentStep.ordinal > index) SkyBluePalette.Sky500
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
            tint = if (isWifiEnabled) SkyBluePalette.SuccessBlue else MaterialTheme.colorScheme.onSurface
        )

        Text(
            text = if (isWifiEnabled) "WiFi Enabled" else "Enable WiFi",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        if (isLoading) {
            CircularProgressIndicator()
            Text("Checking WiFi...", style = MaterialTheme.typography.bodyMedium)
        } else {
            if (isWifiEnabled) {
                Text(
                    text = "WiFi is enabled. Looking for ESP32_CAM...",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                Text(
                    text = "Please enable WiFi to continue.",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Button(
                onClick = onCheckWifi,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isWifiEnabled) "Check Again" else "Check WiFi")
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
            tint = SkyBluePalette.Sky500
        )

        Text(
            text = "Connect to ESP32_CAM",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        if (needsUserAction || error != null) {
            Text(
                text = "Please connect manually:",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Network Name:", style = MaterialTheme.typography.labelMedium)
                    Text("ESP32_CAM_P", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(4.dp))
                    Text("Password:", style = MaterialTheme.typography.labelMedium)
                    Text("12345678", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                }
            }

            if (error != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (error.contains("permission", ignoreCase = true)) {
                            Text(
                                text = "This is normal - the app can still detect the connection.",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Light
                            )
                        }
                    }
                }
            }

            Button(
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Settings, null)
                Spacer(Modifier.width(8.dp))
                Text("Open WiFi Settings")
            }

            Button(
                onClick = onRetryAfterSettings,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (isLoading) "Checking..." else "I'm Connected")
            }
        } else {
            Text(
                text = "Attempting to connect automatically...",
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
                    Text("Connect to ESP32_CAM")
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
            tint = SkyBluePalette.WarningBlue
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
            tint = SkyBluePalette.SuccessBlue
        )

        Text(
            text = "Connected Successfully!",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = SkyBluePalette.SuccessBlue
        )

        Text(
            text = "Preparing your workspace...",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}
