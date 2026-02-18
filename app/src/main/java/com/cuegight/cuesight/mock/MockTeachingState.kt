package com.cuegight.cuesight.mock

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MockTeachingState(
    val isStreaming: Boolean = false,
    val isPaused: Boolean = false,
    val currentEmotion: String = "Neutral",
    val confidence: Float = 0.85f,
    val frameCount: Int = 0,
    val emotionsDetected: Int = 0,
    val commandsSent: Int = 0,
    val sessionDurationSeconds: Long = 0,
    val currentFrame: Bitmap? = null
)

class MockTeachingViewModel : ViewModel() {
    
    private val _state = MutableStateFlow(MockTeachingState())
    val state: StateFlow<MockTeachingState> = _state.asStateFlow()
    
    private var sessionJob: Job? = null
    private var timerJob: Job? = null
    
    // Mock emotions cycle
    private val emotionCycle = listOf(
        "Happy" to 0.92f,
        "Happy" to 0.89f,
        "Surprise" to 0.87f,
        "Neutral" to 0.85f,
        "Sad" to 0.91f,
        "Angry" to 0.88f,
        "Neutral" to 0.84f,
        "Happy" to 0.93f
    )
    
    private var emotionIndex = 0
    
    fun startSession() {
        _state.value = _state.value.copy(isStreaming = true, isPaused = false)
        startMockEmotionCycle()
        startTimer()
    }
    
    fun pauseSession() {
        _state.value = _state.value.copy(isPaused = true)
        sessionJob?.cancel()
        timerJob?.cancel()
    }
    
    fun resumeSession() {
        _state.value = _state.value.copy(isPaused = false)
        startMockEmotionCycle()
        startTimer()
    }
    
    fun endSession() {
        sessionJob?.cancel()
        timerJob?.cancel()
        _state.value = MockTeachingState()
    }
    
    private fun startMockEmotionCycle() {
        sessionJob?.cancel()
        sessionJob = viewModelScope.launch {
            while (_state.value.isStreaming && !_state.value.isPaused) {
                delay(3000) // Change emotion every 3 seconds
                
                val (emotion, confidence) = emotionCycle[emotionIndex]
                emotionIndex = (emotionIndex + 1) % emotionCycle.size
                
                val currentState = _state.value
                val emotionChanged = currentState.currentEmotion != emotion
                
                _state.value = currentState.copy(
                    currentEmotion = emotion,
                    confidence = confidence,
                    frameCount = currentState.frameCount + 1,
                    emotionsDetected = if (emotionChanged) currentState.emotionsDetected + 1 else currentState.emotionsDetected,
                    commandsSent = if (emotionChanged) currentState.commandsSent + 1 else currentState.commandsSent
                )
            }
        }
    }
    
    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (_state.value.isStreaming && !_state.value.isPaused) {
                delay(1000)
                _state.value = _state.value.copy(
                    sessionDurationSeconds = _state.value.sessionDurationSeconds + 1
                )
            }
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        sessionJob?.cancel()
        timerJob?.cancel()
    }
}
