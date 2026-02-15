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
                        Text(
                            "Practice Mode",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            studentName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = { viewModel.togglePause() },
                        modifier = Modifier
                            .padding(8.dp)
                            .size(48.dp)
                    ) {
                        Icon(
                            if (state.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            if (state.isPaused) "Resume" else "Pause",
                            modifier = Modifier.size(32.dp)
                        )
                    }
                },
                actions = {
                    Button(
                        onClick = { showEndDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) {
                        Icon(Icons.Default.Close, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("End Session", fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
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
            // Pause overlay
            AnimatedVisibility(
                visible = state.isPaused,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Pause,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Session Paused",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            "Tap play to continue",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            // Session stats card
            SessionStatsCard(
                correctCount = state.correctCount,
                totalCount = state.totalCount
            )

            // Main interaction card (only if not paused)
            if (!state.isPaused) {
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
            icon = {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text(
                    "End Practice Session?",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Session Summary",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "${state.correctCount}",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = CueSightColors.Green
                            )
                            Text("Correct", style = MaterialTheme.typography.bodySmall)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "${state.totalCount}",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text("Total", style = MaterialTheme.typography.bodySmall)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            val accuracy = if (state.totalCount > 0)
                                (state.correctCount * 100 / state.totalCount) else 0
                            Text(
                                "$accuracy%",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text("Accuracy", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showEndDialog = false
                        viewModel.endSession()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("End Session", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showEndDialog = false }) {
                    Text("Continue")
                }
            }
        )
    }
}

@Composable
fun SessionStatsCard(correctCount: Int, totalCount: Int) {
    val accuracy = if (totalCount > 0) (correctCount.toFloat() / totalCount.toFloat()) else 0f
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Text(
                "Session Progress",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            
            Spacer(Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Animated correct count
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    val animatedCorrect = remember { Animatable(0f) }
                    LaunchedEffect(correctCount) {
                        animatedCorrect.animateTo(
                            correctCount.toFloat(),
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                        )
                    }
                    
                    Text(
                        text = "${animatedCorrect.value.toInt()}",
                        fontSize = 42.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = CueSightColors.Green
                    )
                    Text(
                        "Correct",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
                
                Text(
                    "/",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Light,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f)
                )
                
                // Animated total count
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    val animatedTotal = remember { Animatable(0f) }
                    LaunchedEffect(totalCount) {
                        animatedTotal.animateTo(
                            totalCount.toFloat(),
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
                        )
                    }
                    
                    Text(
                        text = "${animatedTotal.value.toInt()}",
                        fontSize = 42.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        "Total",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }
            
            Spacer(Modifier.height(16.dp))
            
            // Progress bar
            LinearProgressIndicator(
                progress = { accuracy },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = CueSightColors.Green,
                trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f),
            )
            
            Spacer(Modifier.height(8.dp))
            
            Text(
                "${(accuracy * 100).toInt()}% Accuracy",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
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
