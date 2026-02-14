package com.cuegight.cuesight.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cuegight.cuesight.data.model.EmotionLog
import com.cuegight.cuesight.data.model.FrameQuality
import com.cuegight.cuesight.data.model.Session
import com.cuegight.cuesight.data.model.SessionMode
import com.cuegight.cuesight.data.model.SessionStatus
import com.cuegight.cuesight.data.repository.EmotionLogRepository
import com.cuegight.cuesight.data.repository.SessionRepository
import com.cuegight.cuesight.service.TcpFrameService
import com.cuegight.cuesight.util.NetworkBindingHelper
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class TeachingState(
    // Session
    val currentSession: Session? = null,
    val studentId: Long = 0,

    // Connection
    val isConnected: Boolean = false,
    val isReconnecting: Boolean = false,
    val ipAddress: String = "192.168.4.1",

    // Streaming
    val isStreaming: Boolean = false,
    val currentFrame: Bitmap? = null,
    val frameCount: Int = 0,

    // Emotion detection
    val detectedEmotion: String = "Waiting...",
    val emotionConfidence: Float = 0f,
    val predictionDetail: String = "",
    val frameQuality: FrameQuality = FrameQuality.OK,
    val emotionStale: Boolean = false,

    // Session controls
    val isPaused: Boolean = false,
    val pauseCount: Int = 0,
    val sessionElapsedSeconds: Long = 0,
    val activeStreamingSeconds: Long = 0,

    // Emotion log
    val emotionLogs: List<EmotionLog> = emptyList(),
    val emotionFrequencyMap: Map<String, Int> = emptyMap(),

    // Errors and navigation
    val error: String = "",
    val warning: String = "",
    val hardwareError: String? = null,
    val connectionLost: Boolean = false,
    val connectionLostTitle: String = "",
    val connectionLostMessage: String = "",
    val toastMessage: String? = null,
    val shouldNavigateBack: Boolean = false,
    val isProcessingPaused: Boolean = false
)

class TeachingViewModel(
    private val sessionRepository: SessionRepository,
    private val emotionLogRepository: EmotionLogRepository,
    private val webSocketService: TcpFrameService
) : ViewModel() {

    companion object {
        // Emotion classification thresholds based on ML Kit face probabilities
        // These values are derived from empirical observation of typical facial expressions
        // and can be tuned for improved accuracy
        private const val HAPPY_SMILE_THRESHOLD = 0.7f      // High smile probability indicates happiness
        private const val SAD_SMILE_THRESHOLD = 0.3f        // Low smile + drooping eye indicates sadness
        private const val SAD_EYE_THRESHOLD = 0.5f
        private const val ANGRY_SMILE_THRESHOLD = 0.2f      // Very low smile + wide eyes indicates anger
        private const val ANGRY_EYE_THRESHOLD = 0.7f
        private const val SURPRISED_EYE_THRESHOLD = 0.8f    // Very wide eyes + moderate smile indicates surprise
        private const val SURPRISED_SMILE_MIN = 0.3f
        private const val SURPRISED_SMILE_MAX = 0.6f
    }

    private val _state = MutableStateFlow(TeachingState())
    val state: StateFlow<TeachingState> = _state.asStateFlow()

    private var faceDetector: FaceDetector? = null
    private var isDetecting = false
    private var sessionTimerJob: Job? = null
    private var activeStreamingTimerJob: Job? = null
    private var frameWatchdogJob: Job? = null
    private var emotionLogJob: Job? = null
    private var lastFrameReceivedAt = 0L
    private var lastEmotionSent: String = ""
    private var appContext: Context? = null

    // Emotion buffering to reduce command traffic (synchronized for thread safety)
    private val emotionBuffer = mutableListOf<String>()
    private val emotionBufferLock = Any()
    private var lastEmotionSentTime = 0L
    private val EMOTION_SEND_INTERVAL_MS = 2000L  // Send every 2 seconds

    init {
        initFaceDetector()
        setupWebSocketCallbacks()
    }

    fun setContext(context: Context) {
        appContext = context.applicationContext
    }

    private fun initFaceDetector() {
        try {
            faceDetector = FaceDetection.getClient(
                FaceDetectorOptions.Builder()
                    .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                    .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                    .build()
            )
            Log.d("TeachingViewModel", "Face detector initialized")
        } catch (e: Exception) {
            Log.e("TeachingViewModel", "Face detector init error: ${e.message}", e)
            _state.value = _state.value.copy(error = "Face detector init error: ${e.message}")
        }
    }

    private fun setupWebSocketCallbacks() {
        webSocketService.setFrameCallback { bytes -> handleFrame(bytes) }
        webSocketService.setMessageCallback { message -> handleMessage(message) }
        webSocketService.setConnectionStatusCallback { connected ->
            _state.value = _state.value.copy(isConnected = connected)
            if (!connected && _state.value.currentSession != null && !_state.value.connectionLost) {
                handleConnectionLost()
            }
        }
    }

    fun startSession(studentId: Long) {
        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(studentId = studentId)

                val sessionId = sessionRepository.insertSession(
                    Session(
                        studentId = studentId,
                        mode = SessionMode.TEACHING,
                        status = SessionStatus.ACTIVE
                    )
                )

                val session = sessionRepository.getSessionById(sessionId)
                _state.value = _state.value.copy(currentSession = session)

                // Start collecting emotion logs
                emotionLogJob?.cancel()
                emotionLogJob = viewModelScope.launch {
                    emotionLogRepository.getEmotionsBySession(sessionId).collect { logs ->
                        _state.value = _state.value.copy(
                            emotionLogs = logs,
                            emotionFrequencyMap = calculateEmotionFrequency(logs)
                        )
                    }
                }

                // Start session timer
                startSessionTimer()

                Log.d("TeachingViewModel", "Session started: $sessionId")
            } catch (e: Exception) {
                Log.e("TeachingViewModel", "Failed to start session: ${e.message}", e)
                _state.value = _state.value.copy(error = "Failed to start session: ${e.message}")
            }
        }
    }

    private fun startSessionTimer() {
        sessionTimerJob?.cancel()
        sessionTimerJob = viewModelScope.launch {
            while (isActive) {
                delay(1000)
                _state.value = _state.value.copy(
                    sessionElapsedSeconds = _state.value.sessionElapsedSeconds + 1
                )
            }
        }
    }

    private fun startActiveStreamingTimer() {
        activeStreamingTimerJob?.cancel()
        activeStreamingTimerJob = viewModelScope.launch {
            while (isActive) {
                delay(1000)
                if (_state.value.isStreaming && !_state.value.isPaused) {
                    _state.value = _state.value.copy(
                        activeStreamingSeconds = _state.value.activeStreamingSeconds + 1
                    )
                }
            }
        }
    }

    private fun calculateEmotionFrequency(logs: List<EmotionLog>): Map<String, Int> {
        return logs
            .filter { it.emotion !in listOf("SESSION_PAUSED", "SESSION_RESUMED", "No face") }
            .groupBy { it.emotion }
            .mapValues { it.value.size }
    }

    fun connect() {
        viewModelScope.launch {
            try {
                // CRITICAL FIX: Bind to WiFi network before connecting
                appContext?.let { context ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        val network = NetworkBindingHelper.bindToWiFiNetwork(context)
                        network?.let { webSocketService.bindToNetwork(it) }
                    }
                }

                val result = webSocketService.connect(_state.value.ipAddress)
                if (result) {
                    _state.value = _state.value.copy(isConnected = true, error = "")
                    webSocketService.sendCommand("MODE:TEACHING")
                    delay(100)
                    startStreaming()
                } else {
                    _state.value = _state.value.copy(
                        toastMessage = "Failed to connect to ESP32",
                        error = "Connection failed. Check ESP32 WiFi connection."
                    )
                }
            } catch (e: Exception) {
                Log.e("TeachingViewModel", "Connection error: ${e.message}", e)
                _state.value = _state.value.copy(
                    toastMessage = "Connection error",
                    error = e.message ?: "Unknown error"
                )
            }
        }
    }

    fun manualConnect() {
        connect()
    }

    fun retryConnection() {
        _state.value = _state.value.copy(
            connectionLost = false,
            isReconnecting = true
        )
        connect()
    }

    fun startStreaming() {
        if (_state.value.isStreaming) return

        viewModelScope.launch {
            try {
                webSocketService.sendCommand("STREAM:START")
                _state.value = _state.value.copy(
                    isStreaming = true,
                    error = "",
                    warning = ""
                )
                lastFrameReceivedAt = System.currentTimeMillis()
                startFrameWatchdog()
                startActiveStreamingTimer()
                Log.d("TeachingViewModel", "Streaming started")
            } catch (e: Exception) {
                Log.e("TeachingViewModel", "Failed to start streaming: ${e.message}", e)
            }
        }
    }

    fun stopStreaming() {
        webSocketService.sendCommand("STREAM:STOP")
        _state.value = _state.value.copy(
            isStreaming = false,
            currentFrame = null
        )
        frameWatchdogJob?.cancel()
        activeStreamingTimerJob?.cancel()
        Log.d("TeachingViewModel", "Streaming stopped")
    }

    fun pauseSession() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _state.value = _state.value.copy(
                    isPaused = true,
                    pauseCount = _state.value.pauseCount + 1
                )

                // Stop streaming
                webSocketService.sendCommand("STREAM:STOP")
                _state.value = _state.value.copy(isStreaming = false)

                // Display pause message on OLED (0 seconds = persistent)
                webSocketService.sendCommand("OLED:0:Session Paused")

                // Log pause event
                _state.value.currentSession?.let { session ->
                    emotionLogRepository.insertEmotion(
                        EmotionLog(
                            sessionId = session.id,
                            emotion = "SESSION_PAUSED",
                            confidence = 1.0f,
                            frameQuality = FrameQuality.OK
                        )
                    )
                }

                Log.d("TeachingViewModel", "Session paused")
            } catch (e: Exception) {
                Log.e("TeachingViewModel", "Error pausing session: ${e.message}", e)
            }
        }
    }

    fun resumeSession() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _state.value = _state.value.copy(isPaused = false)

                // Send commands to resume
                webSocketService.sendCommand("MODE:TEACHING")
                delay(100)
                webSocketService.sendCommand("STREAM:START")
                _state.value = _state.value.copy(isStreaming = true)

                // Restart timers
                lastFrameReceivedAt = System.currentTimeMillis()
                startFrameWatchdog()

                // Log resume event
                _state.value.currentSession?.let { session ->
                    emotionLogRepository.insertEmotion(
                        EmotionLog(
                            sessionId = session.id,
                            emotion = "SESSION_RESUMED",
                            confidence = 1.0f,
                            frameQuality = FrameQuality.OK
                        )
                    )
                }

                Log.d("TeachingViewModel", "Session resumed")
            } catch (e: Exception) {
                Log.e("TeachingViewModel", "Error resuming session: ${e.message}", e)
            }
        }
    }

    private fun startFrameWatchdog() {
        frameWatchdogJob?.cancel()
        frameWatchdogJob = viewModelScope.launch {
            while (isActive) {
                delay(5000)
                if (_state.value.isStreaming && !_state.value.isPaused) {
                    val elapsed = System.currentTimeMillis() - lastFrameReceivedAt
                    if (elapsed > 5000) {
                        _state.value = _state.value.copy(
                            warning = "No frames received. Check ESP32 connection."
                        )
                    }
                }
            }
        }
    }

    private fun handleFrame(bytes: ByteArray) {
        if (bytes.isEmpty()) return
        if (_state.value.isProcessingPaused || _state.value.isPaused) return

        lastFrameReceivedAt = System.currentTimeMillis()
        _state.value = _state.value.copy(warning = "") // Clear warning when frame received

        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return
        val newFrameCount = _state.value.frameCount + 1
        _state.value = _state.value.copy(
            currentFrame = bitmap,
            frameCount = newFrameCount
        )

        // Process every 3rd frame to reduce load
        if (newFrameCount % 3 == 0) {
            detectEmotion(bitmap)
        }
    }

    private fun handleMessage(message: String) {
        Log.d("TeachingViewModel", "Received message: $message")
        if (message.startsWith("ERROR:")) {
            if (message.startsWith("ERROR:LOW_MEMORY")) {
                _state.value = _state.value.copy(
                    hardwareError = "ESP32 ran out of memory. Please end the session."
                )
                stopStreaming()
            } else {
                _state.value = _state.value.copy(error = message)
            }
        }
    }

    private fun handleConnectionLost() {
        _state.value = _state.value.copy(
            connectionLost = true,
            connectionLostTitle = "Connection Lost",
            connectionLostMessage = "The connection to CueSight glasses was interrupted. Your session data is safe."
        )
    }

    private fun detectEmotion(bitmap: Bitmap) {
        val detector = faceDetector ?: return
        if (isDetecting) return

        isDetecting = true
        val image = InputImage.fromBitmap(bitmap, 0)

        detector.process(image)
            .addOnSuccessListener { faces ->
                handleFaces(faces)
            }
            .addOnFailureListener { e ->
                Log.e("TeachingViewModel", "Detection failed: ${e.message}", e)
            }
            .addOnCompleteListener {
                isDetecting = false
            }
    }

    private fun handleFaces(faces: List<Face>) {
        if (faces.isEmpty()) {
            _state.value = _state.value.copy(
                frameQuality = FrameQuality.NO_FACE,
                detectedEmotion = "No face",
                emotionStale = true,
                predictionDetail = ""
            )
            if (lastEmotionSent != "No face") {
                webSocketService.sendCommand("EMOTION:No face")
                lastEmotionSent = "No face"
            }
            return
        }

        // Get the first (largest) face
        val face = faces[0]
        val smilingProbability = face.smilingProbability ?: 0f
        val leftEyeOpenProbability = face.leftEyeOpenProbability ?: 0f
        val rightEyeOpenProbability = face.rightEyeOpenProbability ?: 0f

        // Classify emotion using heuristic
        val (emotion, confidence) = classifyEmotion(
            smilingProbability,
            leftEyeOpenProbability,
            rightEyeOpenProbability
        )

        _state.value = _state.value.copy(
            detectedEmotion = emotion,
            emotionConfidence = confidence,
            frameQuality = FrameQuality.OK,
            emotionStale = false,
            predictionDetail = ""
        )

        // Buffer emotion instead of sending immediately
        synchronized(emotionBufferLock) {
            emotionBuffer.add(emotion)
        }
        
        // Send most common emotion every 2 seconds
        val now = System.currentTimeMillis()
        if (now - lastEmotionSentTime >= EMOTION_SEND_INTERVAL_MS) {
            val mostCommon = synchronized(emotionBufferLock) {
                if (emotionBuffer.isNotEmpty()) {
                    val result = emotionBuffer.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
                    emotionBuffer.clear()
                    result
                } else {
                    null
                }
            }
            
            if (mostCommon != null && mostCommon != lastEmotionSent) {
                webSocketService.sendCommand("EMOTION:$mostCommon")
                lastEmotionSent = mostCommon
                lastEmotionSentTime = now

                // Log to database (using average confidence for buffered emotion)
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        _state.value.currentSession?.let { session ->
                            emotionLogRepository.insertEmotion(
                                EmotionLog(
                                    sessionId = session.id,
                                    emotion = mostCommon,
                                    confidence = 0.7f,  // Default confidence for buffered emotions
                                    frameQuality = FrameQuality.OK,
                                    smilingProbability = null,  // Probabilities don't correspond to buffered emotion
                                    leftEyeOpenProbability = null,
                                    rightEyeOpenProbability = null
                                )
                            )
                        }
                    } catch (e: Exception) {
                        Log.e("TeachingViewModel", "Failed to log emotion: ${e.message}", e)
                    }
                }
            }
        }
    }

    /**
     * Classifies emotion using ML Kit face probabilities (heuristic approach).
     * Uses threshold constants from companion object.
     * Future enhancement: Replace with TensorFlow Lite emotion model for improved accuracy.
     */
    private fun classifyEmotion(
        smiling: Float,
        leftEye: Float,
        rightEye: Float
    ): Pair<String, Float> {
        return when {
            smiling > HAPPY_SMILE_THRESHOLD -> "Happy" to smiling
            smiling < SAD_SMILE_THRESHOLD && leftEye < SAD_EYE_THRESHOLD -> "Sad" to (1f - smiling)
            smiling < ANGRY_SMILE_THRESHOLD && leftEye > ANGRY_EYE_THRESHOLD && rightEye > ANGRY_EYE_THRESHOLD -> "Angry" to (1f - smiling)
            leftEye > SURPRISED_EYE_THRESHOLD && rightEye > SURPRISED_EYE_THRESHOLD && smiling in SURPRISED_SMILE_MIN..SURPRISED_SMILE_MAX -> "Surprised" to leftEye
            else -> "Neutral" to 0.5f
        }
    }

    fun endSession(status: SessionStatus = SessionStatus.COMPLETED, notes: String = "") {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Stop streaming
                stopStreaming()

                // Disconnect WebSocket
                webSocketService.disconnect()

                // Update session in database
                _state.value.currentSession?.let { session ->
                    val duration = _state.value.sessionElapsedSeconds
                    val emotionCount = _state.value.emotionLogs.size

                    sessionRepository.updateSession(
                        session.copy(
                            endTime = System.currentTimeMillis(),
                            durationSeconds = duration,
                            totalEmotionsDetected = emotionCount,
                            status = status,
                            notes = notes
                        )
                    )
                }

                // Cancel jobs
                sessionTimerJob?.cancel()
                activeStreamingTimerJob?.cancel()
                frameWatchdogJob?.cancel()
                emotionLogJob?.cancel()

                _state.value = _state.value.copy(
                    currentSession = null,
                    shouldNavigateBack = true
                )

                Log.d("TeachingViewModel", "Session ended with status: $status")
            } catch (e: Exception) {
                Log.e("TeachingViewModel", "Error ending session: ${e.message}", e)
            }
        }
    }

    fun sendLEDCommand(command: String) {
        webSocketService.sendCommand("LED:$command")
    }

    fun clearToast() {
        _state.value = _state.value.copy(toastMessage = null)
    }

    fun onNavigationHandled() {
        _state.value = _state.value.copy(shouldNavigateBack = false)
    }

    fun acknowledgeHardwareError() {
        _state.value = _state.value.copy(hardwareError = null)
    }

    fun setIpAddress(ip: String) {
        _state.value = _state.value.copy(ipAddress = ip)
        webSocketService.setIpAddress(ip)
    }

    fun onAppBackgrounded(context: Context) {
        _state.value = _state.value.copy(isProcessingPaused = true)
        Log.d("TeachingViewModel", "App backgrounded, processing paused")
    }

    fun onAppForegrounded(context: Context) {
        _state.value = _state.value.copy(isProcessingPaused = false)
        Log.d("TeachingViewModel", "App foregrounded, processing resumed")
    }

    override fun onCleared() {
        super.onCleared()
        // Auto-save as INTERRUPTED if session is still active
        viewModelScope.launch(Dispatchers.IO) {
            if (_state.value.currentSession?.status == SessionStatus.ACTIVE) {
                endSession(SessionStatus.INTERRUPTED, "Session interrupted")
            }
        }
        faceDetector?.close()
        webSocketService.disconnect()

        // Unbind network when ViewModel is destroyed
        appContext?.let { context ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                NetworkBindingHelper.unbindNetwork(context)
            }
        }

        Log.d("TeachingViewModel", "ViewModel cleared")
    }
}
