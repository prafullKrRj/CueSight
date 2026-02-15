package com.cuegight.cuesight.feature.practice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cuegight.cuesight.core.network.HttpCommandSender
import com.cuegight.cuesight.core.network.PracticeCommandType
import com.cuegight.cuesight.core.util.EmotionMapper
import com.cuegight.cuesight.feature.practice.data.entity.PracticeGuess
import com.cuegight.cuesight.feature.practice.data.entity.PracticeSession
import com.cuegight.cuesight.feature.practice.data.repository.PracticeRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import java.util.UUID

/**
 * New Practice Mode ViewModel - Teacher-controlled feedback
 * Features:
 * - NO camera streaming
 * - Teacher selects: "Which emotion did you show?"
 * - Teacher selects: "What did student guess?"
 * - Compares and sends feedback to ESP32
 * - Tracks session stats and saves to database
 */
class NewPracticeViewModel(
    private val commandSender: HttpCommandSender,
    private val practiceRepository: PracticeRepository
) : ViewModel() {

    private val _state = MutableStateFlow(PracticeState())
    val state: StateFlow<PracticeState> = _state.asStateFlow()

    private var sessionStartTime: Long = 0
    private var sessionId: String = ""
    private var roundStartTime: Long = 0 // Track when teacher selects emotion
    private val roundHistory = mutableListOf<PracticeRound>()

    fun startSession(studentId: Long, studentName: String) {
        sessionStartTime = System.currentTimeMillis()
        sessionId = UUID.randomUUID().toString()
        
        _state.value = _state.value.copy(
            studentId = studentId,
            studentName = studentName,
            isSessionActive = true,
            currentStep = PracticeStep.WAITING_FOR_TEACHER
        )
        
        // Save session to database
        viewModelScope.launch(Dispatchers.IO) {
            practiceRepository.insertSession(
                PracticeSession(
                    sessionId = sessionId,
                    studentId = studentId,
                    startTime = sessionStartTime
                )
            )
        }
        
        // Send "?" to OLED to start
        sendQuestionCommand()
    }

    private fun sendQuestionCommand() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isSendingCommand = true)
            val success = commandSender.sendPracticeCommand(PracticeCommandType.QUESTION)
            _state.value = _state.value.copy(isSendingCommand = false)
            
            if (!success) {
                _state.value = _state.value.copy(
                    error = "Failed to send command to glasses"
                )
            }
        }
    }

    fun onTeacherEmotionSelected(emotion: String) {
        roundStartTime = System.currentTimeMillis() // Track round start time
        _state.value = _state.value.copy(
            teacherEmotion = emotion,
            currentStep = PracticeStep.WAITING_FOR_STUDENT
        )
    }

    fun onStudentGuessSelected(guess: String) {
        _state.value = _state.value.copy(
            studentGuess = guess,
            currentStep = PracticeStep.CHECKING
        )
        
        // Compare and send feedback
        checkAnswer()
    }

    private fun checkAnswer() {
        viewModelScope.launch {
            val teacherEmotion = _state.value.teacherEmotion ?: return@launch
            val studentGuess = _state.value.studentGuess ?: return@launch
            
            val guessTimestamp = System.currentTimeMillis()
            val responseTime = guessTimestamp - roundStartTime // Calculate actual response time
            val isCorrect = teacherEmotion.equals(studentGuess, ignoreCase = true)
            
            _state.value = _state.value.copy(
                isCorrect = isCorrect,
                currentStep = PracticeStep.SHOWING_RESULT,
                isSendingCommand = true
            )
            
            // Send feedback to ESP32
            val commandType = if (isCorrect) PracticeCommandType.CORRECT else PracticeCommandType.WRONG
            val success = commandSender.sendPracticeCommand(commandType)
            
            _state.value = _state.value.copy(isSendingCommand = false)
            
            if (!success) {
                _state.value = _state.value.copy(
                    error = "Failed to send feedback to glasses"
                )
            }
            
            // Update stats
            val newCorrect = _state.value.correctCount + if (isCorrect) 1 else 0
            val newTotal = _state.value.totalCount + 1
            
            _state.value = _state.value.copy(
                correctCount = newCorrect,
                totalCount = newTotal
            )
            
            // Record round (in-memory)
            roundHistory.add(PracticeRound(
                roundNumber = newTotal,
                teacherEmotion = teacherEmotion,
                studentGuess = studentGuess,
                isCorrect = isCorrect,
                timestamp = guessTimestamp
            ))
            
            // Save guess to database with correct response time
            launch(Dispatchers.IO) {
                practiceRepository.insertGuess(
                    PracticeGuess(
                        sessionId = sessionId,
                        timestamp = guessTimestamp,
                        teacherEmotion = teacherEmotion,
                        userGuess = studentGuess,
                        isCorrect = isCorrect,
                        responseTimeMs = responseTime
                    )
                )
            }
            
            // Auto-proceed to next round after 3 seconds
            delay(3000)
            startNextRound()
        }
    }

    fun startNextRound() {
        _state.value = _state.value.copy(
            teacherEmotion = null,
            studentGuess = null,
            isCorrect = null,
            currentStep = PracticeStep.WAITING_FOR_TEACHER,
            error = null
        )
        
        sendQuestionCommand()
    }

    fun endSession() {
        val sessionDuration = System.currentTimeMillis() - sessionStartTime
        val accuracy = if (_state.value.totalCount > 0) {
            (_state.value.correctCount.toFloat() / _state.value.totalCount.toFloat()) * 100f
        } else 0f
        
        val sessionStats = PracticeSessionStats(
            durationMs = sessionDuration,
            totalRounds = _state.value.totalCount,
            correctAnswers = _state.value.correctCount,
            accuracy = accuracy
        )
        
        // Update session in database with final stats
        viewModelScope.launch(Dispatchers.IO) {
            practiceRepository.updateSession(
                PracticeSession(
                    sessionId = sessionId,
                    studentId = _state.value.studentId,
                    startTime = sessionStartTime,
                    endTime = System.currentTimeMillis(),
                    totalGuesses = _state.value.totalCount,
                    correctGuesses = _state.value.correctCount,
                    sessionAccuracy = accuracy / 100f // Convert to 0-1 range
                )
            )
        }
        
        _state.value = _state.value.copy(
            isSessionActive = false,
            sessionStats = sessionStats,
            shouldNavigateBack = true
        )
    }

    fun onNavigated() {
        _state.value = _state.value.copy(shouldNavigateBack = false)
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }
    
    fun togglePause() {
        _state.value = _state.value.copy(isPaused = !_state.value.isPaused)
    }
}

data class PracticeState(
    val studentId: Long = 0,
    val studentName: String = "",
    val isSessionActive: Boolean = false,
    val isPaused: Boolean = false,
    val currentStep: PracticeStep = PracticeStep.WAITING_FOR_TEACHER,
    val teacherEmotion: String? = null,
    val studentGuess: String? = null,
    val isCorrect: Boolean? = null,
    val correctCount: Int = 0,
    val totalCount: Int = 0,
    val isSendingCommand: Boolean = false,
    val error: String? = null,
    val sessionStats: PracticeSessionStats? = null,
    val shouldNavigateBack: Boolean = false
)

enum class PracticeStep {
    WAITING_FOR_TEACHER,   // Teacher needs to select emotion they showed
    WAITING_FOR_STUDENT,   // Teacher needs to enter what student guessed
    CHECKING,              // Comparing answers
    SHOWING_RESULT         // Showing result (✓ or ✗)
}

data class PracticeRound(
    val roundNumber: Int,
    val teacherEmotion: String,
    val studentGuess: String,
    val isCorrect: Boolean,
    val timestamp: Long
)

data class PracticeSessionStats(
    val durationMs: Long,
    val totalRounds: Int,
    val correctAnswers: Int,
    val accuracy: Float
)
