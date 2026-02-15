package com.cuegight.cuesight.feature.teaching

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cuegight.cuesight.core.network.HttpCommandSender
import com.cuegight.cuesight.core.network.HttpMjpegStreamService
import com.cuegight.cuesight.core.util.EmotionMapper
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/**
 * New Teaching ViewModel using HTTP streaming
 * Features:
 * - HTTP MJPEG streaming from ESP32
 * - Emotion detection using MLKit
 * - HTTP command sending (only when emotion changes)
 * - In-memory session tracking (NO database logging)
 */
class NewTeachingViewModel(
    private val streamService: HttpMjpegStreamService,
    private val commandSender: HttpCommandSender
) : ViewModel() {

    private val _state = MutableStateFlow(TeachingState())
    val state: StateFlow<TeachingState> = _state.asStateFlow()

    private var streamingJob: Job? = null
    private var sessionStartTime: Long = 0
    private var lastSentEmotion: String? = null
    private val emotionHistory = mutableListOf<EmotionRecord>()

    fun startSession(studentId: Long, studentName: String) {
        sessionStartTime = System.currentTimeMillis()
        _state.value = _state.value.copy(
            studentId = studentId,
            studentName = studentName,
            isSessionActive = true
        )
        startStreaming()
    }

    fun startStreaming() {
        streamingJob?.cancel()
        streamingJob = viewModelScope.launch {
            _state.value = _state.value.copy(isStreaming = true, streamError = null)
            
            try {
                streamService.streamFrames()
                    .catch { e ->
                        _state.value = _state.value.copy(
                            streamError = e.message ?: "Stream error",
                            isStreaming = false
                        )
                    }
                    .collect { bitmap ->
                        onFrameReceived(bitmap)
                    }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    streamError = e.message ?: "Unknown error",
                    isStreaming = false
                )
            }
        }
    }

    private fun onFrameReceived(bitmap: Bitmap) {
        _state.value = _state.value.copy(
            currentFrame = bitmap,
            frameCount = _state.value.frameCount + 1
        )
        
        // TODO: Add MLKit emotion detection here
        // For now, we'll simulate emotion detection
        // In real implementation, use MLKit Face Detection + classification
        val detectedEmotion = detectEmotion(bitmap)
        
        // Only send if emotion changed
        if (detectedEmotion != lastSentEmotion && detectedEmotion != null) {
            sendEmotionCommand(detectedEmotion)
            lastSentEmotion = detectedEmotion
            
            // Record emotion (in-memory)
            emotionHistory.add(EmotionRecord(
                emotion = detectedEmotion,
                timestamp = System.currentTimeMillis(),
                confidence = 0.85f // TODO: Get actual confidence from MLKit
            ))
            
            _state.value = _state.value.copy(
                currentEmotion = detectedEmotion,
                emotionCount = emotionHistory.size
            )
        }
    }

    private fun detectEmotion(bitmap: Bitmap): String? {
        // TODO: Implement MLKit face detection and emotion classification
        // This is a placeholder that returns null for now
        // Real implementation will use:
        // 1. MLKit Face Detection to detect faces
        // 2. Facial feature analysis to classify emotion
        // 3. Return emotion string (Happy, Sad, Angry, etc.)
        return null
    }

    private fun sendEmotionCommand(emotion: String) {
        viewModelScope.launch {
            val success = commandSender.sendEmotion(emotion)
            if (success) {
                _state.value = _state.value.copy(
                    lastCommandSent = emotion,
                    commandsSentCount = _state.value.commandsSentCount + 1
                )
            }
        }
    }

    fun stopStreaming() {
        streamingJob?.cancel()
        streamingJob = null
        _state.value = _state.value.copy(isStreaming = false)
    }

    fun pauseSession() {
        stopStreaming()
        _state.value = _state.value.copy(isPaused = true)
    }

    fun resumeSession() {
        _state.value = _state.value.copy(isPaused = false)
        startStreaming()
    }

    fun endSession() {
        stopStreaming()
        
        val sessionDuration = System.currentTimeMillis() - sessionStartTime
        val sessionStats = SessionStats(
            durationMs = sessionDuration,
            emotionsDetected = emotionHistory.size,
            commandsSent = _state.value.commandsSentCount,
            framesProcessed = _state.value.frameCount
        )
        
        _state.value = _state.value.copy(
            isSessionActive = false,
            sessionStats = sessionStats,
            shouldNavigateBack = true
        )
    }

    fun updateSessionTime() {
        viewModelScope.launch {
            while (_state.value.isSessionActive && !_state.value.isPaused) {
                delay(1000)
                val elapsed = (System.currentTimeMillis() - sessionStartTime) / 1000
                _state.value = _state.value.copy(sessionElapsedSeconds = elapsed)
            }
        }
    }

    fun onNavigated() {
        _state.value = _state.value.copy(shouldNavigateBack = false)
    }

    override fun onCleared() {
        super.onCleared()
        stopStreaming()
    }
}

data class TeachingState(
    val studentId: Long = 0,
    val studentName: String = "",
    val isSessionActive: Boolean = false,
    val isStreaming: Boolean = false,
    val isPaused: Boolean = false,
    val currentFrame: Bitmap? = null,
    val currentEmotion: String? = null,
    val lastCommandSent: String? = null,
    val streamError: String? = null,
    val frameCount: Int = 0,
    val emotionCount: Int = 0,
    val commandsSentCount: Int = 0,
    val sessionElapsedSeconds: Long = 0,
    val sessionStats: SessionStats? = null,
    val shouldNavigateBack: Boolean = false
)

data class EmotionRecord(
    val emotion: String,
    val timestamp: Long,
    val confidence: Float
)

data class SessionStats(
    val durationMs: Long,
    val emotionsDetected: Int,
    val commandsSent: Int,
    val framesProcessed: Int
)
