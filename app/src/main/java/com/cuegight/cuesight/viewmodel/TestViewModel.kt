package com.cuegight.cuesight.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cuegight.cuesight.data.model.FrameQuality
import com.cuegight.cuesight.service.TcpFrameService
import com.cuegight.cuesight.util.NetworkBindingHelper
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.max

data class TestState(
    val currentFrame: Bitmap? = null,
    val detectedEmotion: String = "No emotion detected",
    val frameCount: Int = 0,
    val isStreaming: Boolean = false,
    val isConnected: Boolean = false,
    val error: String = "",
    val warning: String = "",
    val ipAddress: String = "192.168.4.1",
    val frameQuality: FrameQuality = FrameQuality.OK,
    val predictionDetail: String = "",
    val toastMessage: String? = null,
    val shouldNavigateBack: Boolean = false
)

class TestViewModel(
    private val webSocketService: TcpFrameService
) : ViewModel() {

    private var faceDetector: FaceDetector? = null
    private var streamJob: Job? = null
    private var isDetecting = false
    private var appContext: Context? = null

    // Emotion buffering to reduce command traffic (synchronized for thread safety)
    private val emotionBuffer = mutableListOf<String>()
    private val emotionBufferLock = Any()
    private var lastEmotionSentTime = 0L
    private val EMOTION_SEND_INTERVAL_MS = 2000L  // Send every 2 seconds

    private val _state = MutableStateFlow(TestState())
    val state: StateFlow<TestState> = _state.asStateFlow()

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
        } catch (e: Exception) {
            _state.value = _state.value.copy(error = "Face detector init error: ${e.message}")
        }
    }

    private fun setupWebSocketCallbacks() {
        webSocketService.setFrameCallback { bytes -> handleFrame(bytes) }
        webSocketService.setMessageCallback { message -> handleMessage(message) }
        webSocketService.setConnectionStatusCallback { connected ->
            _state.value = _state.value.copy(isConnected = connected)
        }
    }

    fun setIpAddress(ip: String) {
        _state.value = _state.value.copy(ipAddress = ip)
        webSocketService.setIpAddress(ip)
    }

    fun startSession() {
        viewModelScope.launch {
            delay(500)
            val connected = webSocketService.connect(_state.value.ipAddress)
            _state.value = _state.value.copy(isConnected = connected)

            if (connected) {
                webSocketService.sendCommand("MODE:TEACHING")
            }
        }
    }

    fun connect() {
        viewModelScope.launch {
            // CRITICAL FIX: Bind to WiFi network before connecting
            appContext?.let { context ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val network = NetworkBindingHelper.bindToWiFiNetwork(context)
                    network?.let { webSocketService.bindToNetwork(it) }
                }
            }

            val result = webSocketService.connect(_state.value.ipAddress)
            if (result) {
                webSocketService.sendCommand("MODE:TEACHING")
                _state.value = _state.value.copy(toastMessage = "Connected to ESP32")
            } else {
                _state.value = _state.value.copy(toastMessage = "Failed to connect")
            }
        }
    }

    fun startStreaming() {
        if (streamJob?.isActive == true) return
        streamJob = viewModelScope.launch {
            _state.value = _state.value.copy(isStreaming = true, error = "", warning = "")
            webSocketService.sendCommand("STREAM:START")
        }
    }

    fun stopStreaming() {
        streamJob?.cancel()
        webSocketService.sendCommand("STREAM:STOP")
        _state.value = _state.value.copy(isStreaming = false, currentFrame = null)
    }

    fun endSession() {
        stopStreaming()
        webSocketService.disconnect()
        _state.value = _state.value.copy(shouldNavigateBack = true)
    }

    fun sendLEDCommand(command: String) {
        when (command) {
            "ON" -> webSocketService.sendCommand("LED:ON")
            "OFF" -> webSocketService.sendCommand("LED:OFF")
        }
    }

    fun clearToast() {
        _state.value = _state.value.copy(toastMessage = null)
    }

    fun onNavigationHandled() {
        _state.value = _state.value.copy(shouldNavigateBack = false)
    }

    private fun handleFrame(bytes: ByteArray) {
        if (bytes.isEmpty()) return
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return
        val newFrameCount = _state.value.frameCount + 1
        _state.value = _state.value.copy(currentFrame = bitmap, frameCount = newFrameCount)
        if (newFrameCount % 3 == 0) detectEmotion(bitmap)
    }

    private fun handleMessage(message: String) {
        if (message.startsWith("ERROR:")) {
            _state.value = _state.value.copy(error = message)
        }
    }

    private fun detectEmotion(bitmap: Bitmap) {
        val detector = faceDetector ?: return
        if (isDetecting) return
        isDetecting = true
        val image = InputImage.fromBitmap(bitmap, 0)
        detector.process(image)
            .addOnSuccessListener { faces -> handleFaces(faces) }
            .addOnFailureListener { e -> Log.e("TestViewModel", "Detection failed", e) }
            .addOnCompleteListener { isDetecting = false }
    }

    private fun handleFaces(faces: List<Face>) {
        if (faces.isEmpty()) {
            _state.value = _state.value.copy(frameQuality = FrameQuality.NO_FACE, predictionDetail = "No face")
            return
        }

        val face = faces[0]
        val prediction = predictEmotion(face)
        val lowConfidence = prediction.confidence < 0.6f

        _state.value = _state.value.copy(
            detectedEmotion = prediction.label,
            frameQuality = if (lowConfidence) FrameQuality.POOR else FrameQuality.OK,
            predictionDetail = if (lowConfidence) "Confidence ${(prediction.confidence * 100).toInt()}%" else ""
        )

        // Buffer emotion instead of sending immediately
        if (!lowConfidence) {
            synchronized(emotionBufferLock) {
                emotionBuffer.add(stripEmoji(prediction.label))
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
                if (mostCommon != null) {
                    webSocketService.sendCommand("EMOTION:$mostCommon")
                    lastEmotionSentTime = now
                }
            }
        }
    }

    private fun predictEmotion(face: Face): TestEmotionPrediction {
        val smiling = face.smilingProbability ?: 0f
        val leftEye = face.leftEyeOpenProbability ?: 0f
        val rightEye = face.rightEyeOpenProbability ?: 0f
        val sleepyScore = 1f - max(leftEye, rightEye)

        return listOf(
            TestEmotionPrediction("Happy 😊", smiling),
            TestEmotionPrediction("Sleepy 😴", sleepyScore),
            TestEmotionPrediction("Sad 😢", 1f - smiling),
            TestEmotionPrediction("Neutral 😐", 0.5f)
        ).maxByOrNull { it.confidence }!!
    }

    private fun stripEmoji(label: String) = label.replace(Regex("[😊😢😴😐]"), "").trim()

    override fun onCleared() {
        super.onCleared()
        stopStreaming()
        webSocketService.disconnect()
        faceDetector?.close()

        // Unbind network when ViewModel is destroyed
        appContext?.let { context ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                NetworkBindingHelper.unbindNetwork(context)
            }
        }
    }
}

private data class TestEmotionPrediction(val label: String, val confidence: Float)


