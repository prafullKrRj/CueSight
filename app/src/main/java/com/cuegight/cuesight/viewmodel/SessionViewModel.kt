package com.cuegight.cuesight.viewmodel

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cuegight.cuesight.R
import com.cuegight.cuesight.data.model.EmotionLog
import com.cuegight.cuesight.data.model.FrameQuality
import com.cuegight.cuesight.data.model.Session
import com.cuegight.cuesight.data.model.SessionMode
import com.cuegight.cuesight.data.model.SessionStatus
import com.cuegight.cuesight.data.repository.EmotionLogRepository
import com.cuegight.cuesight.data.repository.SessionRepository
import com.cuegight.cuesight.service.WebSocketService
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
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.max

private const val DEFAULT_ESP32_IP = "192.168.4.1"
private const val TCP_PORT = 81
private const val FRAME_TIMEOUT_MS = 5_000L
private const val FRAME_LOSS_WINDOW_MS = 10_000L
private const val TARGET_FPS = 15f
private const val SESSION_TIMEOUT_MS = 60 * 60 * 1000L
private const val BACKGROUND_TIMEOUT_MS = 5 * 60 * 1000L
private const val RECONNECT_TIMEOUT_MS = 30_000L
private const val CONNECTION_RETRY_LIMIT = 3
private const val NOTIFICATION_CHANNEL_ID = "session_progress"
private const val NOTIFICATION_ID = 1001
private const val PRACTICE_OLED_PLACEHOLDER = "?"

data class SessionState(
    val currentSession: Session? = null,
    val currentFrame: Bitmap? = null,
    val detectedEmotion: String = "No emotion detected",
    val emotionLogs: List<EmotionLog> = emptyList(),
    val frameCount: Int = 0,
    val isStreaming: Boolean = false,
    val isProcessingPaused: Boolean = false,
    val isConnected: Boolean = false,
    val error: String = "",
    val warning: String = "",
    val ipAddress: String = DEFAULT_ESP32_IP,
    val frameQuality: FrameQuality = FrameQuality.OK,
    val predictionDetail: String = "",
    val emotionStale: Boolean = false,
    val connectionLost: Boolean = false,
    val connectionLostTitle: String = "Connection Lost",
    val connectionLostMessage: String = "ESP32 disconnected. Session data is safe.",
    val isReconnecting: Boolean = false,
    val toastMessage: String? = null,
    val canSubmitFeedback: Boolean = true,
    val shouldNavigateToReport: Boolean = false,
    val shouldNavigateBack: Boolean = false,
    val hardwareError: String? = null
)

class SessionViewModel(
    private val sessionRepository: SessionRepository,
    private val emotionLogRepository: EmotionLogRepository,
    private val webSocketService: WebSocketService
) : ViewModel() {

    private var streamJob: Job? = null
    private var frameWatchdogJob: Job? = null
    private var faceDetector: FaceDetector? = null
    private var activeMode: SessionMode = SessionMode.PRACTICE
    private var lastFrameReceivedAt = 0L
    private val frameTimestamps = ArrayDeque<Long>()
    private var maxFrameLossPercent = 0f
    private var lastPredictionLabel = "Neutral"
    private var isDetecting = false
    private var pendingReconnect = false
    private var backgroundedAt: Long? = null
    private var emotionLogJob: Job? = null
    private var appContext: Context? = null

    private val _state = MutableStateFlow(SessionState())
    val state: StateFlow<SessionState> = _state.asStateFlow()

    init {
        initFaceDetector()
        setupSocketCallbacks()
    }

    private fun setupSocketCallbacks() {
        webSocketService.setFrameCallback { bytes ->
            lastFrameReceivedAt = System.currentTimeMillis()
            handleFrame(bytes)
        }
        webSocketService.setMessageCallback { message ->
            if (message.startsWith("ERROR:LOW_MEMORY")) {
                stopStreaming()
                _state.value = _state.value.copy(
                    hardwareError = "ESP32 ran out of memory. Session will be saved after you close this dialog."
                )
            }
        }
        webSocketService.setConnectionStatusCallback { connected ->
            _state.value = _state.value.copy(isConnected = connected)
            if (!connected && _state.value.isStreaming && !pendingReconnect) {
                handleConnectionLost(
                    title = "Connection Lost",
                    message = "ESP32 disconnected. Session data is safe."
                )
            }
        }
    }

    private fun initFaceDetector() {
        try {
            faceDetector = FaceDetection.getClient(
                FaceDetectorOptions.Builder()
                    .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                    .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                    .build()
            )
        } catch (e: Exception) {
            _state.value = _state.value.copy(
                error = "Initialization Error: ${e.message}"
            )
        }
    }

    fun setIpAddress(ip: String) {
        _state.value = _state.value.copy(ipAddress = ip)
    }

    fun setContext(context: Context) {
        appContext = context.applicationContext
    }

    fun manualConnect() {
        viewModelScope.launch {
            Log.d("SessionViewModel", "🔧 MANUAL CONNECT TRIGGERED")
            Log.d("SessionViewModel", "IP: ${_state.value.ipAddress}")
            val result = connectWebSocket(_state.value.ipAddress, startStream = false)
            Log.d("SessionViewModel", "Manual connect result: $result")
            if (result) {
                _state.value = _state.value.copy(toastMessage = "Connected to ESP32!")
            } else {
                _state.value = _state.value.copy(toastMessage = "Failed to connect to ESP32")
            }
        }
    }

    fun startSession(studentId: Long, mode: SessionMode) {
        activeMode = mode
        viewModelScope.launch {
            try {
                Log.d("SessionViewModel", "=== START SESSION ===")
                Log.d("SessionViewModel", "StudentId: $studentId, Mode: $mode")
                Log.d("SessionViewModel", "IP Address: ${_state.value.ipAddress}")

                // Auto-connect to ESP32 to show connection status - MUST happen before early return!
                delay(500) // Small delay to let UI settle
                Log.d("SessionViewModel", "Attempting TCP connection to ${_state.value.ipAddress}:$TCP_PORT")
                val connected = connectWebSocket(_state.value.ipAddress, startStream = false)
                Log.d("SessionViewModel", "Connection result: $connected")

                if (studentId <= 0L) {
                    emotionLogJob?.cancel()
                    _state.value = _state.value.copy(
                        currentSession = null,
                        emotionLogs = emptyList()
                    )
                    Log.d("SessionViewModel", "Test mode - no session saved (studentId = 0)")
                    return@launch
                }
                val sessionId = sessionRepository.insertSession(
                    Session(
                        studentId = studentId,
                        mode = mode
                    )
                )

                val session = sessionRepository.getSessionById(sessionId)
                _state.value = _state.value.copy(currentSession = session)

                emotionLogJob?.cancel()
                emotionLogJob = viewModelScope.launch {
                    emotionLogRepository.getEmotionsBySession(sessionId).collect { logs ->
                        _state.value = _state.value.copy(emotionLogs = logs)
                    }
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = "Failed to start session: ${e.message}")
            }
        }
    }

    fun startStreaming() {
        if (streamJob?.isActive == true) return
        streamJob = viewModelScope.launch {
            _state.value = _state.value.copy(
                isStreaming = true,
                error = "",
                warning = "",
                connectionLost = false,
                isReconnecting = false,
                shouldNavigateToReport = false,
                frameQuality = FrameQuality.OK,
                detectedEmotion = if (activeMode == SessionMode.PRACTICE) PRACTICE_OLED_PLACEHOLDER else "No emotion detected",
                predictionDetail = if (activeMode == SessionMode.PRACTICE) {
                    "Teacher demonstrates. Student guesses."
                } else {
                    ""
                },
                emotionStale = false,
                canSubmitFeedback = true
            )
            lastFrameReceivedAt = System.currentTimeMillis()

            if (_state.value.isConnected) {
                if (activeMode != SessionMode.PRACTICE) {
                    startFrameWatchdog()
                }
            } else {
                val connected = connectWebSocket(
                    _state.value.ipAddress,
                    startStream = activeMode != SessionMode.PRACTICE
                )
                if (!connected) {
                    handleConnectionLost(
                        title = "Connection Lost",
                        message = "Unable to connect to ESP32. Session data is safe."
                    )
                }
            }
        }
    }

    fun stopStreaming() {
        streamJob?.cancel()
        frameWatchdogJob?.cancel()
        webSocketService.disconnect()
        _state.value = _state.value.copy(
            isStreaming = false,
            isConnected = false,
            currentFrame = null,
            warning = "",
            connectionLost = false,
            isReconnecting = false,
            isProcessingPaused = false
        )
    }

    fun endSession(status: SessionStatus = SessionStatus.COMPLETED, extraNote: String = "") {
        viewModelScope.launch {
            if (_state.value.currentSession == null) {
                stopStreaming()
                _state.value = _state.value.copy(shouldNavigateBack = true)
                return@launch
            }
            _state.value.currentSession?.let { session ->
                val duration = (System.currentTimeMillis() - session.startTime) / 1000
                val emotionCount = emotionLogRepository.getEmotionCountForSession(session.id)
                val notes = buildSessionNotes(session.notes, extraNote)

                sessionRepository.updateSession(
                    session.copy(
                        endTime = System.currentTimeMillis(),
                        durationSeconds = duration,
                        totalEmotionsDetected = emotionCount,
                        status = status,
                        notes = notes
                    )
                )

                emotionLogJob?.cancel()
                stopStreaming()
                _state.value = _state.value.copy(
                    currentSession = null,
                    shouldNavigateBack = true
                )
            }
        }
    }

    fun sendFeedback(feedback: String) {
        sendWebSocketCommand("FEEDBACK:$feedback")
    }

    fun sendShowAnswer() {
        sendWebSocketCommand("FEEDBACK:${stripEmoji(lastPredictionLabel)}")
    }

    fun sendLEDCommand(command: String) {
        when (command) {
            "ON" -> sendWebSocketCommand("LED:ON")
            "OFF" -> sendWebSocketCommand("LED:OFF")
        }
    }

    fun retryConnection() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isReconnecting = true)
            pendingReconnect = true

            // Re-bind to WiFi network before reconnecting
            appContext?.let { context ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    Log.d("SessionViewModel", "Re-binding to WiFi network for reconnection...")
                    val network = NetworkBindingHelper.bindToWiFiNetwork(context)
                    if (network != null) {
                        Log.d("SessionViewModel", "✅ Network re-bound successfully")
                        webSocketService.bindToNetwork(network)
                    } else {
                        Log.w("SessionViewModel", "⚠️ Could not re-bind to WiFi network")
                    }
                }
            }

            val success = withTimeoutOrNull(RECONNECT_TIMEOUT_MS) {
                val targetIp = if (_state.value.ipAddress.isBlank()) {
                    DEFAULT_ESP32_IP
                } else {
                    _state.value.ipAddress
                }
                if (_state.value.ipAddress.isBlank()) {
                    _state.value = _state.value.copy(ipAddress = DEFAULT_ESP32_IP)
                }
                repeat(CONNECTION_RETRY_LIMIT) { attempt ->
                    if (connectWebSocket(targetIp, startStream = true)) {
                        return@withTimeoutOrNull true
                    }
                    delay(1000L * (attempt + 1))
                }
                false
            } ?: false

            if (success) {
                _state.value = _state.value.copy(
                    connectionLost = false,
                    isReconnecting = false,
                    isProcessingPaused = false,
                    toastMessage = "Reconnected successfully"
                )
            } else {
                autoSaveInterrupted("Reconnection failed")
            }
        }
    }

    fun clearToast() {
        _state.value = _state.value.copy(toastMessage = null)
    }

    fun acknowledgeHardwareError() {
        _state.value = _state.value.copy(hardwareError = null)
        autoSaveInterrupted("Hardware error")
    }

    fun onNavigationHandled() {
        _state.value = _state.value.copy(
            shouldNavigateToReport = false,
            shouldNavigateBack = false
        )
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    fun onAppBackgrounded(context: Context) {
        backgroundedAt = System.currentTimeMillis()
        _state.value = _state.value.copy(isProcessingPaused = true)
        showBackgroundNotification(context)
    }

    fun onAppForegrounded(context: Context) {
        dismissBackgroundNotification(context)
        _state.value = _state.value.copy(isProcessingPaused = false)
        val pausedAt = backgroundedAt
        backgroundedAt = null
        if (pausedAt != null && System.currentTimeMillis() - pausedAt > BACKGROUND_TIMEOUT_MS) {
            autoSaveInterrupted("Auto-saved after background timeout")
        } else if (!_state.value.isConnected && _state.value.isStreaming) {
            retryConnection()
        }
    }

    private suspend fun connectWebSocket(ipAddress: String, startStream: Boolean): Boolean {
        return try {
            Log.d("SessionViewModel", "connectWebSocket() called - IP: $ipAddress, startStream: $startStream")
            appContext?.let { context ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val network = NetworkBindingHelper.bindToWiFiNetwork(context)
                    network?.let { webSocketService.bindToNetwork(it) }
                }
            }

            val connected = webSocketService.connect(ipAddress)
            if (connected) {
                _state.value = _state.value.copy(isConnected = true, error = "")
                pendingReconnect = false
                lastFrameReceivedAt = System.currentTimeMillis()
                if (startStream) {
                    startFrameWatchdog()
                }
            }
            connected
        } catch (e: Exception) {
            Log.e("SessionViewModel", "TCP connection error: ${e.message}", e)
            false
        }
    }

    private fun startFrameWatchdog() {
        frameWatchdogJob?.cancel()
        frameWatchdogJob = viewModelScope.launch {
            while (isActive) {
                delay(1000)
                if (!_state.value.isStreaming || _state.value.isProcessingPaused) continue
                val elapsed = System.currentTimeMillis() - lastFrameReceivedAt
                if (elapsed > FRAME_TIMEOUT_MS) {
                    handleConnectionLost(
                        title = "Connection Lost",
                        message = "ESP32 disconnected. Session data is safe."
                    )
                }
                checkSessionTimeout()
            }
        }
    }

    private fun checkSessionTimeout() {
        val session = _state.value.currentSession ?: return
        if (System.currentTimeMillis() - session.startTime > SESSION_TIMEOUT_MS) {
            autoSaveInterrupted("Auto-saved after 60 minutes")
        }
    }

    private fun handleFrame(bytes: ByteArray) {
        if (bytes.isEmpty()) {
            return
        }
        updateFrameLossStats()
        if (_state.value.isProcessingPaused) return

        val bitmap = decodeFrame(bytes) ?: return
        val newFrameCount = _state.value.frameCount + 1
        _state.value = _state.value.copy(
            currentFrame = bitmap,
            frameCount = newFrameCount
        )
        if (newFrameCount % 3 == 0) {
            detectEmotion(bitmap)
        }
    }

    private fun decodeFrame(bytes: ByteArray): Bitmap? {
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    private fun updateFrameLossStats() {
        val now = System.currentTimeMillis()
        frameTimestamps.addLast(now)
        while (frameTimestamps.isNotEmpty() && now - frameTimestamps.first() > FRAME_LOSS_WINDOW_MS) {
            frameTimestamps.removeFirst()
        }
        val windowDuration = max(1L, now - frameTimestamps.first()) / 1000f
        val expectedFrames = TARGET_FPS * windowDuration
        if (expectedFrames <= 0) return
        val lossPercent = max(
            0f,
            ((expectedFrames - frameTimestamps.size) / expectedFrames) * 100f
        )
        maxFrameLossPercent = max(maxFrameLossPercent, lossPercent)
        if (lossPercent > 10f) {
            _state.value = _state.value.copy(
                warning = "Poor connection quality (${lossPercent.toInt()}% frame loss)"
            )
        } else if (_state.value.warning.isNotEmpty()) {
            _state.value = _state.value.copy(warning = "")
        }
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
                Log.e("SessionViewModel", "Detection failed: ${e.message}", e)
            }
            .addOnCompleteListener {
                isDetecting = false
            }
    }

    private fun handleFaces(faces: List<Face>) {
        if (faces.isEmpty()) {
            _state.value = _state.value.copy(
                frameQuality = FrameQuality.NO_FACE,
                predictionDetail = "",
                emotionStale = true,
                canSubmitFeedback = activeMode != SessionMode.PRACTICE
            )
            viewModelScope.launch {
                logEmotion(
                    emotion = "Neutral",
                    confidence = 0f,
                    frameQuality = FrameQuality.NO_FACE,
                    smiling = null,
                    leftEyeOpen = null,
                    rightEyeOpen = null
                )
            }
            return
        }

        val face = faces[0]
        val prediction = predictEmotion(face)
        val lowConfidence = prediction.confidence < 0.6f
        lastPredictionLabel = prediction.label

        if (lowConfidence && activeMode != SessionMode.PRACTICE) {
            _state.value = _state.value.copy(
                detectedEmotion = "Uncertain",
                frameQuality = FrameQuality.POOR,
                predictionDetail = "${prediction.label} (${(prediction.confidence * 100).toInt()}%)",
                emotionStale = false,
                canSubmitFeedback = true
            )
            viewModelScope.launch {
                logEmotion(
                    emotion = prediction.label,
                    confidence = prediction.confidence,
                    frameQuality = FrameQuality.POOR,
                    smiling = prediction.smiling,
                    leftEyeOpen = prediction.leftEyeOpen,
                    rightEyeOpen = prediction.rightEyeOpen
                )
            }
            return
        }

        _state.value = _state.value.copy(
            detectedEmotion = prediction.label,
            frameQuality = if (lowConfidence) FrameQuality.POOR else FrameQuality.OK,
            predictionDetail = if (lowConfidence) {
                "Confidence ${(prediction.confidence * 100).toInt()}%"
            } else "",
            emotionStale = false,
            canSubmitFeedback = true
        )

        viewModelScope.launch {
            logEmotion(
                emotion = prediction.label,
                confidence = prediction.confidence,
                frameQuality = if (lowConfidence) FrameQuality.POOR else FrameQuality.OK,
                smiling = prediction.smiling,
                leftEyeOpen = prediction.leftEyeOpen,
                rightEyeOpen = prediction.rightEyeOpen
            )
        }

        if (!lowConfidence && activeMode != SessionMode.PRACTICE) {
            sendWebSocketCommand("EMOTION:${stripEmoji(prediction.label)}")
        } else if (activeMode == SessionMode.PRACTICE) {
            sendWebSocketCommand("EMOTION:$PRACTICE_OLED_PLACEHOLDER")
        }
    }

    private fun predictEmotion(face: Face): EmotionPrediction {
        val smiling = face.smilingProbability ?: 0f
        val leftEyeOpen = face.leftEyeOpenProbability ?: 0f
        val rightEyeOpen = face.rightEyeOpenProbability ?: 0f
        val sleepyScore = 1f - max(leftEyeOpen, rightEyeOpen)
        val happyScore = smiling
        val sadScore = 1f - smiling
        val neutralScore = 0.5f

        val scores = listOf(
            EmotionPrediction("Happy 😊", happyScore, smiling, leftEyeOpen, rightEyeOpen),
            EmotionPrediction("Sleepy 😴", sleepyScore, smiling, leftEyeOpen, rightEyeOpen),
            EmotionPrediction("Sad 😢", sadScore, smiling, leftEyeOpen, rightEyeOpen),
            EmotionPrediction("Neutral 😐", neutralScore, smiling, leftEyeOpen, rightEyeOpen)
        )

        return scores.maxByOrNull { it.confidence } ?: scores.last()
    }

    private suspend fun logEmotion(
        emotion: String,
        confidence: Float,
        frameQuality: FrameQuality,
        smiling: Float?,
        leftEyeOpen: Float?,
        rightEyeOpen: Float?
    ) {
        _state.value.currentSession?.let { session ->
            emotionLogRepository.insertEmotion(
                EmotionLog(
                    sessionId = session.id,
                    emotion = emotion,
                    confidence = confidence,
                    frameQuality = frameQuality,
                    smilingProbability = smiling,
                    leftEyeOpenProbability = leftEyeOpen,
                    rightEyeOpenProbability = rightEyeOpen
                )
            )
        }
    }

    private fun handleConnectionLost(title: String, message: String) {
        _state.value = _state.value.copy(
            connectionLost = true,
            connectionLostTitle = title,
            connectionLostMessage = message,
            isProcessingPaused = true,
            isReconnecting = false
        )
    }

    private fun autoSaveInterrupted(reason: String) {
        val session = _state.value.currentSession ?: return
        viewModelScope.launch {
            val duration = (System.currentTimeMillis() - session.startTime) / 1000
            val emotionCount = emotionLogRepository.getEmotionCountForSession(session.id)
            sessionRepository.updateSession(
                session.copy(
                    endTime = System.currentTimeMillis(),
                    durationSeconds = duration,
                    totalEmotionsDetected = emotionCount,
                    status = SessionStatus.INTERRUPTED,
                    notes = buildSessionNotes(session.notes, reason)
                )
            )
            stopStreaming()
            _state.value = _state.value.copy(
                currentSession = null,
                shouldNavigateToReport = true,
                toastMessage = if (reason.startsWith("Auto-saved")) reason else null
            )
        }
    }

    private fun buildSessionNotes(existing: String, extra: String): String {
        val notes = mutableListOf<String>()
        if (existing.isNotBlank()) {
            notes.add(existing)
        }
        if (extra.isNotBlank()) {
            notes.add(extra)
        }
        if (maxFrameLossPercent > 10f) {
            notes.add("Frame loss ${maxFrameLossPercent.toInt()}%")
        }
        return notes.joinToString(" | ")
    }

    private fun sendWebSocketCommand(command: String) {
        webSocketService.sendCommand(command)
    }

    private fun stripEmoji(label: String): String {
        return label.replace("😊", "")
            .replace("😢", "")
            .replace("😴", "")
            .replace("😐", "")
            .trim()
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private fun showBackgroundNotification(context: Context) {
        if (!canPostNotifications(context)) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Session Progress",
                NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Session in progress")
            .setContentText("Tap to return to CueSight")
            .setOngoing(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            Log.w("SessionViewModel", "Notification permission missing", e)
        }
    }

    private fun dismissBackgroundNotification(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    private fun canPostNotifications(context: Context): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return false
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    override fun onCleared() {
        super.onCleared()
        emotionLogJob?.cancel()
        stopStreaming()
        faceDetector?.close()

        // Unbind network when ViewModel is destroyed
        appContext?.let { context ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                NetworkBindingHelper.unbindNetwork(context)
            }
        }
    }
}

private data class EmotionPrediction(
    val label: String,
    val confidence: Float,
    val smiling: Float?,
    val leftEyeOpen: Float?,
    val rightEyeOpen: Float?
)
