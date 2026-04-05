package com.cuegight.cuesight.feature.practice

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cuegight.cuesight.core.util.EmotionMapper
import com.cuegight.cuesight.ui.theme.SkyBluePalette
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewPracticeScreen(
    studentId: Long,
    studentName: String,
    onNavigateBack: () -> Unit,
    viewModel: NewPracticeViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsState()
    var showEndDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.startSession(studentId, studentName)
    }

    LaunchedEffect(state.shouldNavigateBack) {
        if (state.shouldNavigateBack) {
            onNavigateBack()
            viewModel.onNavigated()
        }
    }

    Scaffold(
        containerColor = SkyBluePalette.Sky50,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Practice Mode", fontWeight = FontWeight.SemiBold)
                        Text(
                            studentName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.togglePause() }) {
                        Icon(
                            if (state.isPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                            contentDescription = "Pause"
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
                        Icon(Icons.Rounded.Close, "End")
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
        ) {
            // 1. Stats Header (Direct State Reading - No Animation)
            // This ensures instant updates when state changes
            ScoreHeader(
                correct = state.correctCount,
                total = state.totalCount
            )

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                shape = RoundedCornerShape(20.dp),
                color = SkyBluePalette.Sky200.copy(alpha = 0.45f)
            ) {
                Text(
                    text = "Teacher selects expression first, then student responds for instant feedback.",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = SkyBluePalette.Sky700
                )
            }

            Spacer(Modifier.height(12.dp))

            // 2. Main Content
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when {
                    state.isPaused -> {
                        PausedView(onResume = { viewModel.togglePause() })
                    }
                    state.currentStep == PracticeStep.SHOWING_RESULT -> {
                        ResultView(
                            isCorrect = state.isCorrect ?: false,
                            teacherEmotion = state.teacherEmotion ?: "",
                            studentGuess = state.studentGuess ?: "",
                            onNextRound = { viewModel.startNextRound() }
                        )
                    }
                    else -> {
                        // Single Window for both inputs
                        PracticeInputView(
                            teacherEmotion = state.teacherEmotion,
                            onTeacherSelect = { viewModel.onTeacherEmotionSelected(it) },
                            onStudentSelect = { viewModel.onStudentGuessSelected(it) }
                        )
                    }
                }
            }
        }
    }

    if (showEndDialog) {
        EndSessionDialog(
            score = "${state.correctCount}/${state.totalCount}",
            onConfirm = {
                showEndDialog = false
                viewModel.endSession()
            },
            onDismiss = { showEndDialog = false }
        )
    }
}

// --- Components ---

@Composable
fun ScoreHeader(correct: Int, total: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "$correct / $total",
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "CURRENT SCORE",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun PracticeInputView(
    teacherEmotion: String?,
    onTeacherSelect: (String) -> Unit,
    onStudentSelect: (String) -> Unit
) {
    val scrollState = rememberScrollState()

    // Logic:
    // If Teacher HAS NOT selected -> Teacher Active, Student Disabled
    // If Teacher HAS selected     -> Teacher Locked (Disabled), Student Active
    val isTeacherDone = teacherEmotion != null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {

        // --- Teacher Section ---
        Column(
            modifier = Modifier.alpha(if (isTeacherDone) 0.5f else 1f)
        ) {
            InputSectionHeader(
                icon = Icons.Rounded.Face,
                title = "Teacher's Expression",
                isActive = !isTeacherDone
            )
            Spacer(Modifier.height(12.dp))
            EmotionGrid(
                selectedEmotion = teacherEmotion,
                onSelect = { if (!isTeacherDone) onTeacherSelect(it) },
                isEnabled = !isTeacherDone, // Disable after selection to prevent changing
                isOutline = false
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

        // --- Student Section ---
        Column(
            modifier = Modifier.alpha(if (isTeacherDone) 1f else 0.4f)
        ) {
            InputSectionHeader(
                icon = Icons.Rounded.Psychology,
                title = "Student's Guess",
                isActive = isTeacherDone
            )
            Spacer(Modifier.height(12.dp))
            EmotionGrid(
                selectedEmotion = null, // Student guesses don't need to stick, they trigger result immediately
                onSelect = { if (isTeacherDone) onStudentSelect(it) },
                isEnabled = isTeacherDone, // Only enabled after teacher selects
                isOutline = true
            )
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
fun InputSectionHeader(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, isActive: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (isActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun EmotionGrid(
    selectedEmotion: String?,
    onSelect: (String) -> Unit,
    isEnabled: Boolean,
    isOutline: Boolean
) {
    val emotions = remember { EmotionMapper.getAllEmotions() }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        emotions.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { emotion ->
                    val isSelected = selectedEmotion == emotion

                    // Visual State Logic
                    val containerColor = when {
                        isSelected -> MaterialTheme.colorScheme.primary
                        isOutline -> Color.Transparent
                        else -> MaterialTheme.colorScheme.surfaceContainerHigh
                    }
                    val contentColor = when {
                        isSelected -> MaterialTheme.colorScheme.onPrimary
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                    val border = if (isOutline && !isSelected)
                        androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    else null

                    Surface(
                        onClick = { onSelect(emotion) },
                        enabled = isEnabled,
                        shape = RoundedCornerShape(12.dp),
                        color = containerColor,
                        border = border,
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "${EmotionMapper.getEmotionEmoji(emotion)} $emotion",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = contentColor
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ResultView(
    isCorrect: Boolean,
    teacherEmotion: String,
    studentGuess: String,
    onNextRound: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = if (isCorrect) Icons.Rounded.CheckCircle else Icons.Rounded.Cancel,
            contentDescription = null,
            tint = if (isCorrect) SkyBluePalette.SuccessBlue else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(96.dp)
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = if (isCorrect) "Correct!" else "Incorrect",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(Modifier.height(32.dp))

        // Comparison Table
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(24.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Showed", style = MaterialTheme.typography.labelSmall)
                    Text(
                        "${EmotionMapper.getEmotionEmoji(teacherEmotion)} $teacherEmotion",
                        fontWeight = FontWeight.Bold
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Guessed", style = MaterialTheme.typography.labelSmall)
                    Text(
                        "${EmotionMapper.getEmotionEmoji(studentGuess)} $studentGuess",
                        fontWeight = FontWeight.Bold,
                        color = if (isCorrect) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        Spacer(Modifier.height(48.dp))

        Button(
            onClick = onNextRound,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isCorrect) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
        ) {
            Text("Next Round", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
fun PausedView(onResume: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.PauseCircle, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onResume) { Text("Resume Session") }
        }
    }
}

@Composable
fun EndSessionDialog(score: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("End Session?", textAlign = TextAlign.Center) },
        text = { Text("Final Score: $score", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
        confirmButton = {
            Button(onClick = onConfirm, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                Text("End")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}