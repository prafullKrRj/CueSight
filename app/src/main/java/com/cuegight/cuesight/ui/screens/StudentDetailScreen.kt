package com.cuegight.cuesight.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.School
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.cuegight.cuesight.data.database.SessionMode
import com.cuegight.cuesight.ui.theme.SkyBluePalette
import com.cuegight.cuesight.viewmodel.StudentViewModel
import org.koin.androidx.compose.koinViewModel
import java.io.File
import java.util.Locale

private const val AVERAGE_FORMAT = "%.1f"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentDetailScreen(
    studentId: Long,
    onNavigateBack: () -> Unit,
    onNavigateToSession: (Long, String, String) -> Unit,
    onNavigateToAnalytics: (Long) -> Unit,
    viewModel: StudentViewModel = koinViewModel()
) {
    val context = LocalContext.current
    val student by viewModel.getStudentById(studentId).collectAsState(initial = null)
    val sessions by viewModel.getSessionsByStudent(studentId).collectAsState(initial = emptyList())

    // --- Core Logic (Unchanged) ---
    val completedSessions = remember(sessions) { sessions.filter { it.endTime != null } }
    val orderedSessions =
        remember(completedSessions) { completedSessions.sortedBy { it.startTime } }

    val totalDurationSeconds = remember(orderedSessions) {
        orderedSessions.sumOf { session ->
            val endTime = session.endTime ?: 0L
            if (endTime > 0) (endTime - session.startTime) / 1000 else 0L
        }
    }
    val totalDurationLabel = remember(totalDurationSeconds) { formatDuration(totalDurationSeconds) }
    val averageGuesses = remember(orderedSessions) {
        if (orderedSessions.isEmpty()) 0f else {
            orderedSessions.sumOf { it.totalGuesses }.toFloat() / orderedSessions.size
        }
    }
    val averageGuessesLabel = remember(averageGuesses) {
        String.format(Locale.US, AVERAGE_FORMAT, averageGuesses)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            CenterAlignedTopAppBar(
                title = { }, // Empty title for ultra-minimal look, or put name here
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            modifier = Modifier.size(28.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { padding ->
        if (student == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(strokeWidth = 2.dp)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(SkyBluePalette.Sky100, SkyBluePalette.Sky50)))
                    .padding(padding)
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(32.dp),
                contentPadding = PaddingValues(bottom = 40.dp)
            ) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        color = SkyBluePalette.Sky200.copy(alpha = 0.42f)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Session Insights",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = SkyBluePalette.Sky900
                            )
                            Text(
                                text = "Track routine consistency and launch tailored activities quickly.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = SkyBluePalette.Sky700
                            )
                        }
                    }
                }

                // 1. Profile Section (Clean, Centered, No Card)
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Avatar
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                            contentAlignment = Alignment.Center
                        ) {
                            if (student?.photoUri != null && File(student?.photoUri!!).exists()) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(File(student?.photoUri!!))
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Text(
                                    text = student?.name?.firstOrNull()?.uppercase() ?: "",
                                    style = MaterialTheme.typography.displayMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        Text(
                            text = student?.name ?: "",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = "${student?.age} years • ${student?.diagnosis ?: "No diagnosis"}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        if (!student?.notes.isNullOrEmpty()) {
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = student?.notes ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .background(
                                        MaterialTheme.colorScheme.surfaceContainer,
                                        RoundedCornerShape(12.dp)
                                    )
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }
                }

                // 2. Stats Section (Horizontal Row, No Card)
                item {
                    if (orderedSessions.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            MinimalStat(
                                label = "Sessions",
                                value = orderedSessions.size.toString(),
                                modifier = Modifier.weight(1f)
                            )
                            MinimalStat(
                                label = "Avg Guesses",
                                value = averageGuessesLabel,
                                modifier = Modifier.weight(1f)
                            )
                            MinimalStat(
                                label = "Time",
                                value = totalDurationLabel,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    } else {
                        Text(
                            text = "No sessions recorded yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // 3. Analytics Button (If data exists)
                if (orderedSessions.isNotEmpty()) {
                    item {
                        ActionRow(
                            title = "View Analytics",
                            icon = Icons.Rounded.Analytics,
                            color = MaterialTheme.colorScheme.tertiary,
                            onClick = { onNavigateToAnalytics(studentId) }
                        )
                    }
                }

                // 4. Session Actions Header
                item {
                    Text(
                        text = "Start Session",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // 5. Session Mode Buttons (Full Width, Large Touch Targets)
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        LargeActionCard(
                            title = "Teaching Mode",
                            subtitle = "Demonstrate emotions",
                            icon = Icons.Rounded.School,
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            onClick = {
                                onNavigateToSession(
                                    studentId,
                                    student?.name ?: "",
                                    SessionMode.TEACHING.name
                                )
                            }
                        )

                        LargeActionCard(
                            title = "Practice Mode",
                            subtitle = "Independent practice",
                            icon = Icons.Rounded.Psychology,
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            onClick = {
                                onNavigateToSession(
                                    studentId,
                                    student?.name ?: "",
                                    SessionMode.PRACTICE.name
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

// --- Minimalist Helper Composables ---

@Composable
fun MinimalStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
fun ActionRow(
    title: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(16.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
            }
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun LargeActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(targetValue = if (isPressed) 0.98f else 1f, label = "scale")

    Surface(
        onClick = { onClick() },
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale),
        shape = RoundedCornerShape(24.dp),
        color = containerColor,
    ) {
        Row(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon in a subtle circle
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(contentColor.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(Modifier.width(20.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor.copy(alpha = 0.7f)
                )
            }

            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = contentColor.copy(alpha = 0.5f)
            )
        }
    }
}

private fun formatDuration(totalSeconds: Long): String {
    if (totalSeconds < 0L) return "—"
    if (totalSeconds < 60L) return "0m"
    val totalMinutes = totalSeconds / 60
    val days = totalMinutes / (60 * 24)
    val hours = (totalMinutes % (60 * 24)) / 60
    val minutes = totalMinutes % 60
    val parts = mutableListOf<String>()
    if (days > 0) parts.add("${days}d")
    if (hours > 0) parts.add("${hours}h")
    if (minutes > 0) parts.add("${minutes}m")
    return parts.joinToString(" ")
}