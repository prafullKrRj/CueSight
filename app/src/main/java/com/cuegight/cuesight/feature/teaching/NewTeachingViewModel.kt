package com.cuegight.cuesight.feature.teaching

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cuegight.cuesight.core.network.HttpCommandSender
import com.cuegight.cuesight.core.network.HttpMjpegStreamService
import com.cuegight.cuesight.core.util.EmotionMapper
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

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
    private var streamingRequested = false

    // MLKit Face Detector
    private val faceDetector by lazy {
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
                .setMinFaceSize(0.15f) // Minimum face size relative to image
                .enableTracking() // Track faces across frames
                .build()
        )
    }

    fun startSession(studentId: Long, studentName: String) {
        Log.d("TeachingVM", "🎯 Starting session for: $studentName (ID: $studentId)")
        sessionStartTime = System.currentTimeMillis()
        _state.value = _state.value.copy(
            studentId = studentId,
            studentName = studentName,
            isSessionActive = true
        )

        // DON'T auto-start streaming - wait for user to click "Start Streaming"
        // startStreaming()

        // Start timer
        updateSessionTime()
    }

    fun startStreaming() {
        if (streamingRequested && streamingJob?.isActive == true) {
            Log.d("TeachingVM", "⚠️ Streaming already in progress")
            return
        }

        streamingRequested = true
        Log.d("TeachingVM", "📡 Starting WebView stream...")

        // Just set the flag - WebView will be shown and screenshots will be captured
        _state.value = _state.value.copy(isStreaming = true, streamError = null)

        // Start timer
        updateSessionTime()
    }

    fun onFrameReceived(bitmap: Bitmap) {
        val currentFrameCount = _state.value.frameCount + 1

        _state.value = _state.value.copy(
            currentFrame = bitmap,
            frameCount = currentFrameCount
        )

        // Log every 30 frames
        if (currentFrameCount % 30 == 0) {
            Log.d("TeachingVM", "📊 Processed $currentFrameCount frames, detecting emotion...")
        }

        // Detect emotion using MLKit
        viewModelScope.launch {
            try {
                val detectedEmotion = detectEmotion(bitmap)

                // Only send if emotion changed
                if (detectedEmotion != lastSentEmotion && detectedEmotion != null) {
                    Log.d("TeachingVM", "😊 Emotion changed: $lastSentEmotion -> $detectedEmotion")
                    sendEmotionCommand(detectedEmotion)
                    lastSentEmotion = detectedEmotion

                    // Record emotion (in-memory)
                    emotionHistory.add(EmotionRecord(
                        emotion = detectedEmotion,
                        timestamp = System.currentTimeMillis(),
                        confidence = 0.85f
                    ))

                    _state.value = _state.value.copy(
                        currentEmotion = detectedEmotion,
                        emotionCount = emotionHistory.size
                    )
                }
            } catch (e: Exception) {
                Log.e("TeachingVM", "❌ Error detecting emotion: ${e.message}")
            }
        }
    }

    private suspend fun detectEmotion(bitmap: Bitmap): String? {
        return try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val faces = faceDetector.process(image).await()
            
            if (faces.isEmpty()) {
                // No face detected
                return null
            }
            
            // Get the first (largest/most prominent) face
            val face = faces[0]
            
            // Get smiling and eye open probabilities
            val smilingProb = face.smilingProbability ?: 0f
            val leftEyeOpenProb = face.leftEyeOpenProbability ?: 1f
            val rightEyeOpenProb = face.rightEyeOpenProbability ?: 1f
            
            // Classify emotion based on facial features
            // This is a simplified classification - can be enhanced
            when {
                // Happy: High smiling probability
                smilingProb > 0.7f -> "Happy"
                
                // Sad: Low smiling, often partially closed eyes
                smilingProb < 0.2f && (leftEyeOpenProb < 0.5f || rightEyeOpenProb < 0.5f) -> "Sad"
                
                // Surprised: Wide eyes (both eyes very open)
                leftEyeOpenProb > 0.9f && rightEyeOpenProb > 0.9f && smilingProb < 0.5f -> "Surprise"
                
                // Angry: Low smiling, tense features (this is harder to detect accurately)
                smilingProb < 0.3f && leftEyeOpenProb > 0.6f && rightEyeOpenProb > 0.6f -> "Angry"
                
                // Neutral: Moderate smiling, normal eye openness
                smilingProb in 0.3f..0.6f -> "Neutral"
                
                // Default to Neutral if no strong indicators
                else -> "Neutral"
            }
        } catch (e: Exception) {
            // If emotion detection fails, return null
            null
        }
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
        streamingRequested = false
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
            while (_state.value.isSessionActive) {
                if (!_state.value.isPaused) {
                    val elapsed = (System.currentTimeMillis() - sessionStartTime) / 1000
                    _state.value = _state.value.copy(sessionElapsedSeconds = elapsed)
                }
                delay(1000)
            }
        }
    }

    fun onNavigated() {
        _state.value = _state.value.copy(shouldNavigateBack = false)
    }

    override fun onCleared() {
        super.onCleared()
        stopStreaming()
        // Note: faceDetector.close() is not necessary as it's managed by MLKit
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
