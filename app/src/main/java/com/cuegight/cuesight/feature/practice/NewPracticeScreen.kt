package com.cuegight.cuesight.feature.practice

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cuegight.cuesight.core.util.EmotionMapper
import com.cuegight.cuesight.ui.theme.CueSightColors
import org.koin.androidx.compose.koinViewModel

/**
 * New Practice Mode Screen - Teacher-controlled feedback interface
 * NO camera streaming, just teacher input and feedback display
 */
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Practice Mode", style = MaterialTheme.typography.titleLarge)
                        Text(
                            studentName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { showEndDialog = true }) {
                        Icon(Icons.Default.Close, "End session")
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
            // Session stats card
            SessionStatsCard(
                correctCount = state.correctCount,
                totalCount = state.totalCount
            )

            // Main interaction card
            when (state.currentStep) {
                PracticeStep.WAITING_FOR_TEACHER -> {
                    TeacherEmotionSelectionCard(
                        onEmotionSelected = { viewModel.onTeacherEmotionSelected(it) }
                    )
                }
                PracticeStep.WAITING_FOR_STUDENT -> {
                    StudentGuessSelectionCard(
                        teacherEmotion = state.teacherEmotion ?: "",
                        onGuessSelected = { viewModel.onStudentGuessSelected(it) }
                    )
                }
                PracticeStep.CHECKING -> {
                    CheckingCard()
                }
                PracticeStep.SHOWING_RESULT -> {
                    ResultCard(
                        isCorrect = state.isCorrect ?: false,
                        teacherEmotion = state.teacherEmotion ?: "",
                        studentGuess = state.studentGuess ?: "",
                        onNextRound = { viewModel.startNextRound() }
                    )
                }
            }

            // Error display
            if (state.error != null) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Default.Error, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                        Text(
                            state.error ?: "",
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }
    }

    // End session dialog
    if (showEndDialog) {
        AlertDialog(
            onDismissRequest = { showEndDialog = false },
            title = { Text("End Practice Session?") },
            text = {
                Text("Score: ${state.correctCount}/${state.totalCount} correct")
            },
            confirmButton = {
                Button(onClick = {
                    showEndDialog = false
                    viewModel.endSession()
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
}

@Composable
fun SessionStatsCard(correctCount: Int, totalCount: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "$correctCount",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = CueSightColors.Green
                )
                Text("Correct", style = MaterialTheme.typography.bodyMedium)
            }
            
            Text("/", fontSize = 24.sp)
            
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "$totalCount",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold
                )
                Text("Total", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun TeacherEmotionSelectionCard(onEmotionSelected: (String) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Which emotion did you show?",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            EmotionMapper.getAllEmotions().forEach { emotion ->
                Button(
                    onClick = { onEmotionSelected(emotion) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("${EmotionMapper.getEmotionEmoji(emotion)} $emotion", fontSize = 18.sp)
                }
            }
        }
    }
}

@Composable
fun StudentGuessSelectionCard(
    teacherEmotion: String,
    onGuessSelected: (String) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "You showed: ${EmotionMapper.getEmotionEmoji(teacherEmotion)} $teacherEmotion",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = "What did the student guess?",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            EmotionMapper.getAllEmotions().forEach { emotion ->
                OutlinedButton(
                    onClick = { onGuessSelected(emotion) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("${EmotionMapper.getEmotionEmoji(emotion)} $emotion", fontSize = 18.sp)
                }
            }
        }
    }
}

@Composable
fun CheckingCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator(modifier = Modifier.size(64.dp))
            Text(
                "Sending feedback...",
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

@Composable
fun ResultCard(
    isCorrect: Boolean,
    teacherEmotion: String,
    studentGuess: String,
    onNextRound: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "result_pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isCorrect) {
                CueSightColors.Green.copy(alpha = 0.2f)
            } else {
                CueSightColors.Red.copy(alpha = 0.2f)
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                if (isCorrect) Icons.Default.CheckCircle else Icons.Default.Cancel,
                contentDescription = null,
                modifier = Modifier.size(80.dp).graphicsLayer { scaleX = scale; scaleY = scale },
                tint = if (isCorrect) CueSightColors.Green else CueSightColors.Red
            )

            Text(
                text = if (isCorrect) "Correct! ✓" else "Wrong ✗",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = if (isCorrect) CueSightColors.Green else CueSightColors.Red
            )

            Text(
                text = "Teacher: ${EmotionMapper.getEmotionEmoji(teacherEmotion)} $teacherEmotion\nStudent: ${EmotionMapper.getEmotionEmoji(studentGuess)} $studentGuess",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge
            )

            Button(
                onClick = onNextRound,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Next Round")
            }
        }
    }
}
