package com.cuegight.cuesight.feature.practice.ui

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cuegight.cuesight.feature.practice.data.entity.PracticeGuess
import com.cuegight.cuesight.feature.practice.data.entity.PracticeSession
import com.cuegight.cuesight.feature.practice.data.entity.TherapistWeight
import com.cuegight.cuesight.feature.practice.data.repository.PracticeRepository
import com.cuegight.cuesight.feature.practice.domain.engine.ErpfEngine
import com.cuegight.cuesight.feature.practice.domain.model.CwaResult
import com.cuegight.cuesight.feature.practice.domain.model.ErpiResult
import com.cuegight.cuesight.feature.practice.domain.model.FeedbackState
import com.cuegight.cuesight.feature.practice.domain.model.MasteryResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import java.util.UUID

class PracticeModeViewModel(
    private val repository: PracticeRepository,
    private val erpfEngine: ErpfEngine,
    private val dataService: DataService
) : ViewModel() {

    companion object {
        private const val TAG = "PracticeModeViewModel"
    }

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

    private val _sessionElapsedTime = MutableStateFlow(0L)
    val sessionElapsedTime: StateFlow<Long> = _sessionElapsedTime.asStateFlow()

    private var emotionSetTimestamp: Long = 0L
    private var timerJob: Job? = null
    private var feedbackDismissJob: Job? = null

    init {
        startNewSession()
        initializeDatabaseDefaults()
        startSessionTimer()
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
                    startTime = _sessionStartTime.value
                )
            )
        }
    }

    private fun initializeDatabaseDefaults() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.initializeMasteryIfEmpty()
            repository.initializeWeightsIfEmpty()
        }
    }

    private fun startSessionTimer() {
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                _sessionElapsedTime.value = System.currentTimeMillis() - _sessionStartTime.value
            }
        }
    }

    fun setTeacherEmotion(emotion: String) {
        if (_cooldownActive.value) return

        _currentTeacherEmotion.value = emotion
        emotionSetTimestamp = System.currentTimeMillis()
    }

    fun submitUserGuess(guess: String) {
        val teacherEmotion = _currentTeacherEmotion.value ?: return
        if (_cooldownActive.value) return

        viewModelScope.launch {
            val responseTime = System.currentTimeMillis() - emotionSetTimestamp
            val isCorrect = teacherEmotion.equals(guess, ignoreCase = true)

            _guessCount.value += 1
            if (isCorrect) {
                _correctCount.value += 1
            }

            sendOledFeedback(isCorrect, teacherEmotion)
            showFeedback(isCorrect, teacherEmotion, guess)

            repository.insertGuess(
                PracticeGuess(
                    sessionId = _currentSessionId.value,
                    timestamp = System.currentTimeMillis(),
                    teacherEmotion = teacherEmotion,
                    userGuess = guess,
                    isCorrect = isCorrect,
                    responseTimeMs = responseTime
                )
            )

            erpfEngine.updateMasteryAfterGuess(teacherEmotion, isCorrect)
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

        _feedbackState.value = FeedbackState(
            isCorrect = isCorrect,
            teacherEmotion = teacherEmotion,
            userGuess = userGuess,
            message = message
        )

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

            val session = repository.getSession(_currentSessionId.value)
            session?.let {
                repository.updateSession(
                    it.copy(
                        endTime = System.currentTimeMillis(),
                        totalGuesses = _guessCount.value,
                        correctGuesses = _correctCount.value,
                        sessionAccuracy = accuracy
                    )
                )
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
            val guesses = repository.getAllGuesses()
            val csvContent = buildCsvContent(guesses)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
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
                null
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
            val existing = repository.getWeight(emotion)
            if (existing == null) {
                repository.insertWeight(TherapistWeight(emotion = emotion, weight = weight))
            } else {
                repository.updateWeight(existing.copy(weight = weight))
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

interface DataService {
    suspend fun sendCommand(command: String)
}
