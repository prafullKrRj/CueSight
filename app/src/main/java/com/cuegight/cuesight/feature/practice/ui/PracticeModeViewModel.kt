package com.cuegight.cuesight.feature.practice.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cuegight.cuesight.feature.practice.data.entity.EmotionMastery
import com.cuegight.cuesight.feature.practice.data.entity.PracticeGuess
import com.cuegight.cuesight.feature.practice.data.entity.PracticeSession
import com.cuegight.cuesight.feature.practice.data.entity.TherapistWeight
import com.cuegight.cuesight.feature.practice.data.repository.PracticeRepository
import com.cuegight.cuesight.feature.practice.domain.engine.ErpfEngine
import com.cuegight.cuesight.feature.practice.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID



class PracticeModeViewModel(
    private val repository: PracticeRepository,
    private val erpfEngine: ErpfEngine,
    private val dataService: DataService
) : ViewModel() {

    private val _currentSessionId = MutableStateFlow(UUID.randomUUID().toString())
    val currentSessionId: StateFlow<String> = _currentSessionId.asStateFlow()

    private val _sessionStartTime = MutableStateFlow(System.currentTimeMillis())

    private val _currentTeacherEmotion = MutableStateFlow<String?>(null)
    val currentTeacherEmotion: StateFlow<String?> = _currentTeacherEmotion.asStateFlow()

    private val _guessCount = MutableStateFlow(0)
    val guessCount: StateFlow<Int> = _guessCount.asStateFlow()

    private val _correctCount = MutableStateFlow(0)
    val correctCount: StateFlow<Int> = _correctCount.asStateFlow()

    private val _feedbackState = MutableStateFlow<FeedbackState?>(null)
    val feedbackState: StateFlow<FeedbackState?> = _feedbackState.asStateFlow()

    private val _cooldownActive = MutableStateFlow(false)
    val cooldownActive: StateFlow<Boolean> = _cooldownActive.asStateFlow()

    private var emotionSetTimestamp: Long = 0L
    private var feedbackJob: Job? = null

    init {
        startNewSession()
    }

    private fun startNewSession() {
        val sessionId = UUID.randomUUID().toString()
        _currentSessionId.value = sessionId
        _sessionStartTime.value = System.currentTimeMillis()
        _guessCount.value = 0
        _correctCount.value = 0

        viewModelScope.launch(Dispatchers.IO) {
            repository.insertSession(
                PracticeSession(
                    sessionId = sessionId,
                    startTime = System.currentTimeMillis()
                )
            )
        }
    }

    fun setTeacherEmotion(emotion: String) {
        if (_cooldownActive.value) return

        _currentTeacherEmotion.value = emotion
        emotionSetTimestamp = System.currentTimeMillis()
    }

    fun submitUserGuess(userGuess: String) {
        val teacherEmotion = _currentTeacherEmotion.value ?: return
        if (_cooldownActive.value) return

        val timestamp = System.currentTimeMillis()
        val responseTime = timestamp - emotionSetTimestamp
        val isCorrect = teacherEmotion.equals(userGuess, ignoreCase = true)

        // update counts
        _guessCount.value += 1
        if (isCorrect) {
            _correctCount.value += 1
        }

        // Show feedback
        val message = if (isCorrect) "Correct! That was $teacherEmotion." else "You guessed $userGuess. The expression was $teacherEmotion."
        _feedbackState.value = FeedbackState(isCorrect, teacherEmotion, userGuess, message)

        // Handle side effects
        viewModelScope.launch {
            // Send to glasses
            val oledMsg = if (isCorrect) "CORRECT! $teacherEmotion" else "WRONG. It was $teacherEmotion"
            try {
                dataService.sendCommand("OLED:5:$oledMsg")
            } catch (e: Exception) {
                Log.e("PracticeVM", "Failed to send OLED command", e)
            }

            // Log to DB
            repository.insertGuess(
                PracticeGuess(
                    sessionId = _currentSessionId.value,
                    timestamp = timestamp,
                    teacherEmotion = teacherEmotion,
                    userGuess = userGuess,
                    isCorrect = isCorrect,
                    responseTimeMs = responseTime
                )
            )

            // Update Mastery
            erpfEngine.updateMasteryAfterGuess(teacherEmotion, isCorrect)

            // Cooldown and reset
            _cooldownActive.value = true
            _currentTeacherEmotion.value = null // Hide valid options or reset selection state

            delay(3000) // 3s cooldown

            _feedbackState.value = null
            _cooldownActive.value = false
        }
    }

    fun endSession() {
        val endTime = System.currentTimeMillis()
        val total = _guessCount.value
        val correct = _correctCount.value
        val accuracy = if (total > 0) correct.toFloat() / total else 0f

        viewModelScope.launch(Dispatchers.IO) {
            val session = repository.getSession(_currentSessionId.value)
            session?.let {
                repository.updateSession(
                    it.copy(
                        endTime = endTime,
                        totalGuesses = total,
                        correctGuesses = correct,
                        sessionAccuracy = accuracy
                    )
                )
            }
        }
    }

    private val _sessionElapsedTime = MutableStateFlow(0L)
    val sessionElapsedTime: StateFlow<Long> = _sessionElapsedTime.asStateFlow()

    private var timerJob: Job? = null
    private var feedbackDismissJob: Job? = null

    init {
        initializeSession()
        startSessionTimer()
    }

    private fun initializeSession() {
        viewModelScope.launch {
            // Create session record
            val session = PracticeSession(
                sessionId = currentSessionId,
                startTime = sessionStartTime
            )
            repository.insertSession(session)

            // Pre-populate tables if needed
            initializeDatabaseDefaults()
        }
    }

    private suspend fun initializeDatabaseDefaults() {
        val emotions = Emotion.getAllEmotions()

        // Check and populate EmotionMastery
        val existingMastery = repository.getAllEmotionMasterySync()
        if (existingMastery.isEmpty()) {
            emotions.forEach { emotion ->
                repository.insertEmotionMastery(
                    EmotionMastery(
                        emotion = emotion,
                        lambda = 1.0f,
                        lastCorrectTimestamp = 0L,
                        totalAttempts = 0,
                        correctAttempts = 0
                    )
                )
            }
        }

        // Check and populate TherapistWeight
        val existingWeights = repository.getAllTherapistWeightsSync()
        if (existingWeights.isEmpty()) {
            emotions.forEach { emotion ->
                repository.insertTherapistWeight(
                    TherapistWeight(
                        emotion = emotion,
                        weight = 0.20f
                    )
                )
            }
        }
    }

    private fun startSessionTimer() {
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                _sessionElapsedTime.value = System.currentTimeMillis() - sessionStartTime
            }
        }
    }

    fun setTeacherEmotion(emotion: String) {
        if (_cooldownActive.value) return

        _currentTeacherEmotion.value = emotion
        emotionSetTimestamp = System.currentTimeMillis()
        Log.d(TAG, "Teacher selected emotion: $emotion")
    }

    fun submitUserGuess(guess: String) {
        val teacherEmotion = _currentTeacherEmotion.value ?: return

        viewModelScope.launch {
            val responseTime = System.currentTimeMillis() - emotionSetTimestamp
            val isCorrect = teacherEmotion.equals(guess, ignoreCase = true)

            // Update counts
            _guessCount.value += 1
            if (isCorrect) {
                _correctCount.value += 1
            }

            // Send OLED feedback
            sendOledFeedback(isCorrect, teacherEmotion)

            // Show phone feedback
            showFeedback(isCorrect, teacherEmotion, guess)

            // Log to database
            val practiceGuess = PracticeGuess(
                sessionId = currentSessionId,
                timestamp = System.currentTimeMillis(),
                teacherEmotion = teacherEmotion,
                userGuess = guess,
                isCorrect = isCorrect,
                responseTimeMs = responseTime
            )
            repository.insertGuess(practiceGuess)

            // Update emotion mastery
            erpfEngine.updateMasteryAfterGuess(teacherEmotion, isCorrect)

            // Reset and cooldown
            resetForNextRound()
            startCooldown()
        }
    }

    private suspend fun sendOledFeedback(isCorrect: Boolean, emotion: String) {
        withContext(Dispatchers.IO) {
            try {
                val message = if (isCorrect) {
                    "OLED:5:CORRECT!"
                } else {
                    "OLED:5:It was $emotion"
                }
                dataService.sendCommand(message)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send OLED command", e)
            }
        }
    }

    private fun showFeedback(isCorrect: Boolean, teacherEmotion: String, userGuess: String) {
        val message = if (isCorrect) {
            "Correct! That was $teacherEmotion."
        } else {
            "You guessed $userGuess. The expression was $teacherEmotion."
        }

        _feedbackState.value = FeedbackState(message, isCorrect)

        // Auto-dismiss after 3 seconds
        feedbackDismissJob?.cancel()
        feedbackDismissJob = viewModelScope.launch {
            delay(3000)
            _feedbackState.value = null
        }
    }

    private fun resetForNextRound() {
        _currentTeacherEmotion.value = null
        emotionSetTimestamp = 0L
    }

    private fun startCooldown() {
        viewModelScope.launch {
            _cooldownActive.value = true
            delay(3000)
            _cooldownActive.value = false
        }
    }

    fun endSession() {
        viewModelScope.launch {
            val accuracy = if (_guessCount.value > 0) {
                _correctCount.value.toFloat() / _guessCount.value.toFloat()
            } else {
                0f
            }

            val session = repository.getSessionById(currentSessionId)
            session?.let {
                val updatedSession = it.copy(
                    endTime = System.currentTimeMillis(),
                    totalGuesses = _guessCount.value,
                    correctGuesses = _correctCount.value,
                    sessionAccuracy = accuracy
                )
                repository.updateSession(updatedSession)
            }

            timerJob?.cancel()
        }
    }

    fun getErpiResult(): Flow<ErpiResult> = flow {
        emit(erpfEngine.computeErpi())
    }

    fun getMasteryResult(): Flow<MasteryResult> = flow {
        emit(erpfEngine.computeMastery())
    }

    fun getCwaResult(): Flow<CwaResult> = flow {
        emit(erpfEngine.computeCwa())
    }

    suspend fun exportSessionsToCsv(context: Context): Uri? = withContext(Dispatchers.IO) {
        try {
            val guesses = repository.getAllGuessesSync()
            val csvContent = buildCsvContent(guesses)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Use MediaStore for Android 10+
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, "cueSight_practice_export_${System.currentTimeMillis()}.csv")
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }

                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                uri?.let {
                    resolver.openOutputStream(it)?.use { outputStream ->
                        OutputStreamWriter(outputStream).use { writer ->
                            writer.write(csvContent)
                        }
                    }
                }
                uri
            } else {
                // Fallback for older versions
                null // Simplified - would need storage permission handling
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export CSV", e)
            null
        }
    }

    private fun buildCsvContent(guesses: List<PracticeGuess>): String {
        val header = "timestamp,session_id,teacher_emotion,user_guess,is_correct,response_time_ms\n"
        val rows = guesses.joinToString("\n") { guess ->
            "${guess.timestamp},${guess.sessionId},${guess.teacherEmotion},${guess.userGuess},${guess.isCorrect},${guess.responseTimeMs}"
        }
        return header + rows
    }

    suspend fun updateTherapistWeights(weights: Map<String, Float>): Boolean {
        val sum = weights.values.sum()
        if (kotlin.math.abs(sum - 1.0f) > 0.01f) {
            Log.w(TAG, "Weights do not sum to 1.0: $sum")
            return false
        }

        weights.forEach { (emotion, weight) ->
            val existing = repository.getTherapistWeightByEmotion(emotion)
            if (existing != null) {
                repository.updateTherapistWeight(existing.copy(weight = weight))
            } else {
                repository.insertTherapistWeight(TherapistWeight(emotion, weight))
            }
        }
        return true
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        feedbackDismissJob?.cancel()
    }
}

// Placeholder for DataService - assumed to exist
interface DataService {
    suspend fun sendCommand(command: String)
}

