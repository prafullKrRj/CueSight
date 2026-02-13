package com.cuegight.cuesight.feature.practice.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.cuegight.cuesight.feature.practice.domain.model.Emotion
import com.cuegight.cuesight.feature.practice.ui.components.*
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PracticeModeScreen(
    onNavigateToAnalytics: () -> Unit,
    viewModel: PracticeModeViewModel = hiltViewModel()
) {
    val currentTeacherEmotion by viewModel.currentTeacherEmotion.collectAsState()
    val guessCount by viewModel.guessCount.collectAsState()
    val correctCount by viewModel.correctCount.collectAsState()
    val feedbackState by viewModel.feedbackState.collectAsState()
    val cooldownActive by viewModel.cooldownActive.collectAsState()
    val elapsedTime by viewModel.sessionElapsedTime.collectAsState()

    val emotions = Emotion.getAllEmotions()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Practice Mode")
                        Text(
                            formatElapsedTime(elapsedTime),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                actions = {
                    Text(
                        "Correct: $correctCount / Total: $guessCount",
                        modifier = Modifier.padding(end = 8.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    IconButton(onClick = {
                        viewModel.endSession()
                        onNavigateToAnalytics()
                    }) {
                        Icon(Icons.Default.Close, "End Session")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Teacher Section
            Card(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text(
                            "Teacher Panel",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Text(
                            "Select the emotion you observe:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    items(emotions.size) { index ->
                        val emotion = emotions[index]
                        EmotionButton(
                            emotion = emotion,
                            color = getEmotionColor(emotion),
                            isSelected = currentTeacherEmotion == emotion,
                            isEnabled = !cooldownActive,
                            onClick = { viewModel.setTeacherEmotion(emotion) }
                        )
                    }

                    if (cooldownActive) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Text(
                                    "Cooldown active... (3s)",
                                    modifier = Modifier.padding(16.dp),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }

            // Student Section
            Card(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "What emotion do you see?",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    // Feedback Card (shows when feedback is active)
                    feedbackState?.let { feedback ->
                        FeedbackCard(
                            message = feedback.message,
                            isCorrect = feedback.isCorrect
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Student Guess Buttons
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(emotions.size) { index ->
                            val emotion = emotions[index]
                            EmotionButton(
                                emotion = emotion,
                                color = getEmotionColor(emotion),
                                isEnabled = currentTeacherEmotion != null && feedbackState == null,
                                onClick = { viewModel.submitUserGuess(emotion) }
                            )
                        }
                    }

                    if (currentTeacherEmotion == null) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Text(
                                "Waiting for teacher to select an emotion...",
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatElapsedTime(milliseconds: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(milliseconds)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(milliseconds) % 60
    return String.format("%02d:%02d", minutes, seconds)
}

