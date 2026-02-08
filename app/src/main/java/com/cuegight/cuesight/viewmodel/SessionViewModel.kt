package com.cuegight.cuesight.viewmodel

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
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
import com.cuegight.cuesight.ml.EmotionClassifier
import com.cuegight.cuesight.ml.EmotionStabilizer
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlin.math.max

private const val UDP_PORT = 4210 // Must match repository root ESP32_CueSight_Final.ino
private const val DEFAULT_WEBSOCKET_PORT = 8888 // Must match repository root ESP32_CueSight_Final.ino
private const val FRAME_TIMEOUT_MS = 5_000L
private const val FRAME_LOSS_WINDOW_MS = 10_000L
private const val TARGET_FPS = 12f
private const val SESSION_TIMEOUT_MS = 60 * 60 * 1000L
private const val BACKGROUND_TIMEOUT_MS = 5 * 60 * 1000L
private const val RECONNECT_TIMEOUT_MS = 30_000L
private const val CONNECTION_RETRY_LIMIT = 3
private const val HANDSHAKE_TIMEOUT_MS = 3_000L
private const val PING_INTERVAL_MS = 10_000L
private const val NOTIFICATION_CHANNEL_ID = "session_progress"
private const val NOTIFICATION_ID = 1001

data class DiscoveredDevice(
    val ipAddress: String,
    val webSocketPort: Int
)

data class SessionState(
    val currentSession: Session? = null,
    val currentFrame: Bitmap? = null,
    val detectedEmotion: String = "No emotion detected",
    val emotionLogs: List<EmotionLog> = emptyList(),
    val frameCount: Int = 0,
    val isStreaming: Boolean = false,
    val isProcessingPaused: Boolean = false,
    val error: String = "",
    val warning: String = "",
    val ipAddress: String = "",
    val webSocketPort: Int = DEFAULT_WEBSOCKET_PORT,
    val discoveredDevice: DiscoveredDevice? = null,
    val isDiscovering: Boolean = false,
    val isConnecting: Boolean = false,
    val handshakeComplete: Boolean = false,
    val connectionStatus: String = "",
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
    private val appContext: Context
) : ViewModel() {

    private val client = OkHttpClient()
    private var streamJob: Job? = null
    private var frameWatchdogJob: Job? = null
    private var discoveryJob: Job? = null
    private var pingJob: Job? = null
    private var webSocket: WebSocket? = null
    private var faceDetector: FaceDetector? = null
    private var emotionClassifier: EmotionClassifier? = null
    private val emotionStabilizer = EmotionStabilizer()
    private var activeMode: SessionMode = SessionMode.PRACTICE
    private var lastFrameReceivedAt = 0L
    private val frameTimestamps = ArrayDeque<Long>()
    private var maxFrameLossPercent = 0f
    private var lastPredictionLabel = "Neutral"
    private var lastStableEmotion: String? = null
    private var lastStableConfidence: Float = 0f
    private var lastStableTrackingId: Int? = null
    private var isDetecting = false
    private var pendingReconnect = false
    private var backgroundedAt: Long? = null
    private var lastPongAt: Long = 0L
    private var emotionLogJob: Job? = null

    private val _state = MutableStateFlow(SessionState())
    val state: StateFlow<SessionState> = _state.asStateFlow()

    init {
        initFaceDetector()
        initEmotionClassifier()
    }

    private fun initFaceDetector() {
        try {
            faceDetector = FaceDetection.getClient(
                FaceDetectorOptions.Builder()
                    .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                    .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                    .setTrackingEnabled(true)
                    .build()
            )
        } catch (e: Exception) {
            _state.value = _state.value.copy(
                error = "Initialization Error: ${e.message}"
            )
        }
    }

    private fun initEmotionClassifier() {
        try {
            emotionClassifier?.close()
            emotionClassifier = EmotionClassifier(appContext)
        } catch (e: Exception) {
            _state.value = _state.value.copy(
                error = "Initialization Error: ${e.message}"
            )
        }
    }

    fun setIpAddress(ip: String) {
        _state.value = _state.value.copy(
            ipAddress = ip,
            webSocketPort = DEFAULT_WEBSOCKET_PORT
        )
    }

    fun setDiscoveredDevice(device: DiscoveredDevice) {
        _state.value = _state.value.copy(
            ipAddress = device.ipAddress,
            webSocketPort = device.webSocketPort,
            discoveredDevice = device
        )
    }

    fun startDiscovery() {
        discoveryJob?.cancel()
        discoveryJob = viewModelScope.launch(Dispatchers.IO) {
            _state.value = _state.value.copy(
                isDiscovering = true,
                connectionStatus = "Scanning for CueSight devices...",
                discoveredDevice = null
            )
            try {
                DatagramSocket(UDP_PORT).use { socket ->
                    socket.broadcast = true
                    socket.soTimeout = 5000
                    val buffer = ByteArray(256)
                    while (isActive) {
                        val packet = DatagramPacket(buffer, buffer.size)
                        socket.receive(packet)
                        val message = String(packet.data, 0, packet.length)
                        val device = parseDiscoveryMessage(message)
                        if (device != null) {
                            _state.value = _state.value.copy(
                                ipAddress = device.ipAddress,
                                webSocketPort = device.webSocketPort,
                                discoveredDevice = device,
                                isDiscovering = false,
                                connectionStatus = "Device found"
                            )
                            break
                        }
                    }
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isDiscovering = false,
                    connectionStatus = "Discovery timed out"
                )
            }
        }
    }

    fun stopDiscovery() {
        discoveryJob?.cancel()
        _state.value = _state.value.copy(isDiscovering = false)
    }

    fun startSession(studentId: Long, mode: SessionMode) {
        activeMode = mode
        viewModelScope.launch {
            try {
                val sessionId = sessionRepository.insertSession(
                    Session(
                        studentId = studentId,
                        mode = mode
                    )
                )

                val session = sessionRepository.getSessionById(sessionId)
                _state.value = _state.value.copy(currentSession = session)
                _state.value = _state.value.copy(connectionStatus = "Ready to connect")

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
                predictionDetail = "",
                emotionStale = false,
                canSubmitFeedback = true,
                isConnecting = true,
                connectionStatus = "Connecting to device...",
                handshakeComplete = false
            )
            lastFrameReceivedAt = System.currentTimeMillis()
            val discoveredDevice = _state.value.discoveredDevice ?: discoverEsp32Device()
            if (discoveredDevice != null) {
                _state.value = _state.value.copy(
                    ipAddress = discoveredDevice.ipAddress,
                    webSocketPort = discoveredDevice.webSocketPort,
                    discoveredDevice = discoveredDevice
                )
            }
            val targetIp = discoveredDevice?.ipAddress ?: _state.value.ipAddress
            val targetPort = discoveredDevice?.webSocketPort ?: _state.value.webSocketPort
            val connected = if (targetIp.isNotBlank()) {
                connectWebSocket(targetIp, targetPort, startStream = true)
            } else {
                false
            }
            if (!connected) {
                handleConnectionLost(
                    title = "Connection Lost",
                    message = "Unable to connect to ESP32. Session data is safe."
                )
            } else {
                _state.value = _state.value.copy(
                    isConnecting = false,
                    connectionStatus = "Connected",
                    handshakeComplete = true
                )
                stopDiscovery()
                startFrameWatchdog()
                startHeartbeat()
            }
        }
    }

    fun stopStreaming() {
        streamJob?.cancel()
        frameWatchdogJob?.cancel()
        pingJob?.cancel()
        sendWebSocketCommand("STREAM:STOP")
        sendWebSocketCommand("MODE:IDLE")
        webSocket?.close(1000, "Stopped")
        webSocket = null
        _state.value = _state.value.copy(
            isStreaming = false,
            currentFrame = null,
            warning = "",
            connectionLost = false,
            isReconnecting = false,
            isProcessingPaused = false,
            isConnecting = false,
            handshakeComplete = false,
            connectionStatus = "Disconnected"
        )
    }

    fun endSession(status: SessionStatus = SessionStatus.COMPLETED, extraNote: String = "") {
        viewModelScope.launch {
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
        val answer = lastStableEmotion ?: lastPredictionLabel
        sendWebSocketCommand("FEEDBACK:${stripEmoji(answer)}")
    }

    fun logStudentGuess(guess: String) {
        val aiEmotion = lastStableEmotion ?: run {
            _state.value = _state.value.copy(toastMessage = "Waiting for a stable detection")
            return
        }
        val isCorrect = stripEmoji(aiEmotion).equals(stripEmoji(guess), ignoreCase = true)
        viewModelScope.launch {
            logEmotion(
                emotion = aiEmotion,
                confidence = lastStableConfidence,
                frameQuality = _state.value.frameQuality,
                smiling = null,
                leftEyeOpen = null,
                rightEyeOpen = null,
                studentGuess = guess,
                isCorrect = isCorrect,
                isStable = true,
                trackingId = lastStableTrackingId
            )
        }
        sendWebSocketCommand(if (isCorrect) "FEEDBACK:CORRECT" else "FEEDBACK:WRONG")
        _state.value = _state.value.copy(
            canSubmitFeedback = false,
            toastMessage = if (isCorrect) "Correct logged" else "Marked incorrect"
        )
    }

    fun sendLEDCommand(command: String) {
        when (command) {
            "ON" -> sendWebSocketCommand("LED:ON")
            "OFF" -> sendWebSocketCommand("LED:OFF")
        }
    }

    fun retryConnection() {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                isReconnecting = true,
                connectionStatus = "Reconnecting..."
            )
            pendingReconnect = true
            val success = withTimeoutOrNull(RECONNECT_TIMEOUT_MS) {
                val discoveredDevice = discoverEsp32Device()
                if (discoveredDevice == null) {
                    _state.value = _state.value.copy(
                        connectionLostTitle = "Device Reset Detected",
                        connectionLostMessage = "ESP32 is not responding to discovery. It may have restarted or be unreachable."
                    )
                }
                val targetIp = discoveredDevice?.ipAddress ?: _state.value.ipAddress
                val targetPort = discoveredDevice?.webSocketPort ?: _state.value.webSocketPort
                if (discoveredDevice != null) {
                    _state.value = _state.value.copy(
                        ipAddress = discoveredDevice.ipAddress,
                        webSocketPort = discoveredDevice.webSocketPort,
                        discoveredDevice = discoveredDevice
                    )
                }
                repeat(CONNECTION_RETRY_LIMIT) { attempt ->
                    if (connectWebSocket(targetIp, targetPort, startStream = true)) {
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
                    toastMessage = "Reconnected successfully",
                    connectionStatus = "Connected",
                    handshakeComplete = true
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
        } else if (webSocket == null && _state.value.isStreaming) {
            retryConnection()
        }
    }

    private suspend fun connectWebSocket(
        ipAddress: String,
        port: Int,
        startStream: Boolean
    ): Boolean {
        return withContext(Dispatchers.IO) {
            val connectionResult = CompletableDeferred<Boolean>()
            val handshakeResult = CompletableDeferred<Boolean>()
            val request = Request.Builder()
                .url("ws://$ipAddress:$port")
                .build()

            webSocket?.close(1000, "Reconnecting")
            webSocket = client.newWebSocket(
                request,
                createWebSocketListener(connectionResult, handshakeResult, startStream)
            )

            val opened = withTimeoutOrNull(10_000L) {
                connectionResult.await()
            } ?: false
            if (!opened) return@withContext false
            withTimeoutOrNull(HANDSHAKE_TIMEOUT_MS) {
                handshakeResult.await()
            } ?: false
        }
    }

    private fun createWebSocketListener(
        connectionResult: CompletableDeferred<Boolean>,
        handshakeResult: CompletableDeferred<Boolean>,
        startStream: Boolean
    ): WebSocketListener {
        return object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                connectionResult.complete(true)
                sendWebSocketCommand("HELLO")
                pendingReconnect = false
                lastFrameReceivedAt = System.currentTimeMillis()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (text == "PONG") {
                    lastPongAt = System.currentTimeMillis()
                    if (!handshakeResult.isCompleted) {
                        handshakeResult.complete(true)
                        onHandshakeComplete(startStream)
                    }
                    return
                }
                if (text == "HELLO_ACK") {
                    if (!handshakeResult.isCompleted) {
                        handshakeResult.complete(true)
                        onHandshakeComplete(startStream)
                    }
                    return
                }
                if (text.startsWith("ERROR:LOW_MEMORY")) {
                    stopStreaming()
                    _state.value = _state.value.copy(
                        hardwareError = "ESP32 ran out of memory. Session will be saved after you close this dialog."
                    )
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                lastFrameReceivedAt = System.currentTimeMillis()
                handleFrame(bytes.toByteArray())
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (!connectionResult.isCompleted) {
                    connectionResult.complete(false)
                }
                if (!handshakeResult.isCompleted) {
                    handshakeResult.complete(false)
                }
                if (_state.value.isStreaming && !pendingReconnect) {
                    handleConnectionLost(
                        title = "Connection Lost",
                        message = "ESP32 disconnected. Session data is safe."
                    )
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (!connectionResult.isCompleted) {
                    connectionResult.complete(false)
                }
                if (!handshakeResult.isCompleted) {
                    handshakeResult.complete(false)
                }
            }
        }
    }

    private fun onHandshakeComplete(startStream: Boolean) {
        sendWebSocketCommand("MODE:${activeMode.name}")
        if (activeMode == SessionMode.PRACTICE) {
            sendWebSocketCommand("EMOTION:HIDDEN")
        }
        if (startStream) {
            sendWebSocketCommand("STREAM:START")
        }
        lastPongAt = System.currentTimeMillis()
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

    private fun startHeartbeat() {
        pingJob?.cancel()
        pingJob = viewModelScope.launch {
            while (isActive) {
                delay(PING_INTERVAL_MS)
                if (!_state.value.isStreaming) continue
                sendWebSocketCommand("PING")
                val elapsed = System.currentTimeMillis() - lastPongAt
                if (elapsed > PING_INTERVAL_MS * 2) {
                    handleConnectionLost(
                        title = "Connection Lost",
                        message = "ESP32 heartbeat missed. Session data is safe."
                    )
                }
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
        updateFrameLossStats()
        if (_state.value.isProcessingPaused) return

        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return
        val newFrameCount = _state.value.frameCount + 1
        _state.value = _state.value.copy(
            currentFrame = bitmap,
            frameCount = newFrameCount
        )
        if (newFrameCount % 3 == 0) {
            detectEmotion(bitmap)
        }
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
                handleFaces(bitmap, faces)
            }
            .addOnFailureListener { e ->
                Log.e("SessionViewModel", "Detection failed: ${e.message}", e)
            }
            .addOnCompleteListener {
                isDetecting = false
            }
    }

    private fun handleFaces(bitmap: Bitmap, faces: List<Face>) {
        if (faces.isEmpty()) {
            _state.value = _state.value.copy(
                detectedEmotion = appContext.getString(R.string.no_face_detected),
                frameQuality = FrameQuality.NO_FACE,
                predictionDetail = "",
                emotionStale = true,
                canSubmitFeedback = activeMode != SessionMode.PRACTICE
            )
            lastStableEmotion = null
            lastStableConfidence = 0f
            lastStableTrackingId = null
            if (activeMode == SessionMode.PRACTICE) {
                sendWebSocketCommand("EMOTION:HIDDEN")
            }
            return
        }

        val face = faces[0]
        val prediction = predictEmotion(bitmap, face) ?: run {
            _state.value = _state.value.copy(
                frameQuality = FrameQuality.POOR,
                predictionDetail = "Low confidence",
                emotionStale = true,
                canSubmitFeedback = activeMode != SessionMode.PRACTICE
            )
            return
        }
        val lowConfidence = prediction.confidence < 0.6f
        lastPredictionLabel = prediction.label

        val stableEmotion = emotionStabilizer.update(face.trackingId, prediction.label)
        val stable = stableEmotion != null
        lastStableEmotion = stableEmotion
        lastStableConfidence = prediction.confidence
        lastStableTrackingId = face.trackingId
        _state.value = _state.value.copy(
            detectedEmotion = stableEmotion ?: appContext.getString(R.string.stabilizing_label),
            frameQuality = if (lowConfidence) FrameQuality.POOR else FrameQuality.OK,
            predictionDetail = "Confidence ${(prediction.confidence * 100).toInt()}%",
            emotionStale = !stable,
            canSubmitFeedback = activeMode != SessionMode.PRACTICE || stable
        )

        if (stable && activeMode == SessionMode.TEACHING) {
            viewModelScope.launch {
                logEmotion(
                    emotion = stableEmotion ?: prediction.label,
                    confidence = prediction.confidence,
                    frameQuality = if (lowConfidence) FrameQuality.POOR else FrameQuality.OK,
                    smiling = null,
                    leftEyeOpen = null,
                    rightEyeOpen = null,
                    isStable = true,
                    trackingId = face.trackingId
                )
            }
            sendWebSocketCommand("EMOTION:${stripEmoji(stableEmotion ?: prediction.label)}")
        } else if (activeMode == SessionMode.PRACTICE) {
            sendWebSocketCommand("EMOTION:HIDDEN")
        }
    }

    private fun predictEmotion(bitmap: Bitmap, face: Face): EmotionPrediction? {
        val classifier = emotionClassifier ?: return null
        val boundingBox = expandRect(face.boundingBox, bitmap.width, bitmap.height)
        val faceCrop = try {
            Bitmap.createBitmap(
                bitmap,
                boundingBox.left,
                boundingBox.top,
                boundingBox.width(),
                boundingBox.height()
            )
        } catch (e: Exception) {
            return null
        }
        val probabilities = try {
            classifier.classify(faceCrop)
        } catch (e: Exception) {
            Log.e("SessionViewModel", "Classifier error: ${e.message}", e)
            return null
        }
        val maxIndex = probabilities.indices.maxByOrNull { probabilities[it] } ?: return null
        val confidence = probabilities[maxIndex]
        if (confidence < 0.6f) return null
        val label = classifier.labels.getOrNull(maxIndex) ?: "Neutral"
        return EmotionPrediction(label, confidence, null, null, null)
    }

    private fun expandRect(rect: Rect, width: Int, height: Int): Rect {
        val expandX = (rect.width() * 0.2f).toInt()
        val expandY = (rect.height() * 0.2f).toInt()
        val left = (rect.left - expandX).coerceAtLeast(0)
        val top = (rect.top - expandY).coerceAtLeast(0)
        val right = (rect.right + expandX).coerceAtMost(width)
        val bottom = (rect.bottom + expandY).coerceAtMost(height)
        return Rect(left, top, right, bottom)
    }

    private suspend fun logEmotion(
        emotion: String,
        confidence: Float,
        frameQuality: FrameQuality,
        smiling: Float?,
        leftEyeOpen: Float?,
        rightEyeOpen: Float?,
        studentGuess: String? = null,
        isCorrect: Boolean? = null,
        isStable: Boolean = false,
        trackingId: Int? = null
    ) {
        _state.value.currentSession?.let { session ->
            emotionLogRepository.insertEmotion(
                EmotionLog(
                    sessionId = session.id,
                    emotion = emotion,
                    confidence = confidence,
                    frameQuality = frameQuality,
                    studentGuess = studentGuess,
                    isCorrect = isCorrect,
                    isStable = isStable,
                    trackingId = trackingId,
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
            isReconnecting = false,
            isConnecting = false,
            handshakeComplete = false,
            connectionStatus = "Disconnected"
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
        val socket = webSocket ?: return
        if (!socket.send(command)) {
            Log.w("SessionViewModel", "Failed to send: $command")
        }
    }

    private suspend fun discoverEsp32Device(): DiscoveredDevice? {
        return withContext(Dispatchers.IO) {
            try {
                DatagramSocket(UDP_PORT).use { socket ->
                    socket.broadcast = true
                    socket.soTimeout = 3000
                    val buffer = ByteArray(256)
                    val responsePacket = DatagramPacket(buffer, buffer.size)
                    socket.receive(responsePacket)
                    val response = String(responsePacket.data, 0, responsePacket.length)
                    parseDiscoveryMessage(response)
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun parseDiscoveryMessage(message: String): DiscoveredDevice? {
        val parts = message.split("|", limit = 3)
        if (parts.size < 3 || parts[0] != "CUESIGHT") return null
        val ipAddress = parts[1]
        val port = parts[2].toIntOrNull() ?: DEFAULT_WEBSOCKET_PORT
        return DiscoveredDevice(ipAddress = ipAddress, webSocketPort = port)
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
        emotionClassifier?.close()
        discoveryJob?.cancel()
        pingJob?.cancel()
    }
}

private data class EmotionPrediction(
    val label: String,
    val confidence: Float,
    val smiling: Float?,
    val leftEyeOpen: Float?,
    val rightEyeOpen: Float?
)
