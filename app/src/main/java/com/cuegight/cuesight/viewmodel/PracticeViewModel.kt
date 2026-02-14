package com.cuegight.cuesight.viewmodel

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cuegight.cuesight.data.model.EmotionLog
import com.cuegight.cuesight.data.model.Session
import com.cuegight.cuesight.data.model.SessionMode
import com.cuegight.cuesight.data.model.SessionStatus
import com.cuegight.cuesight.data.repository.EmotionLogRepository
import com.cuegight.cuesight.data.repository.SessionRepository
import com.cuegight.cuesight.service.WebSocketService
import com.cuegight.cuesight.util.NetworkBindingHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PracticeState(
    val currentSession: Session? = null,
    val emotionLogs: List<EmotionLog> = emptyList(),
    val isStreaming: Boolean = false,
    val isConnected: Boolean = false,
    val error: String = "",
    val warning: String = "",
    val ipAddress: String = "192.168.4.1",
    val canSubmitFeedback: Boolean = true,
    val toastMessage: String? = null,
    val shouldNavigateBack: Boolean = false
)

class PracticeViewModel(
    private val sessionRepository: SessionRepository,
    private val emotionLogRepository: EmotionLogRepository,
    private val webSocketService: WebSocketService
) : ViewModel() {

    private var streamJob: Job? = null
    private var emotionLogJob: Job? = null
    private var appContext: Context? = null

    private val _state = MutableStateFlow(PracticeState())
    val state: StateFlow<PracticeState> = _state.asStateFlow()

    init {
        setupWebSocketCallbacks()
    }

    fun setContext(context: Context) {
        appContext = context.applicationContext
    }

    private fun setupWebSocketCallbacks() {
        webSocketService.setMessageCallback { message -> handleMessage(message) }
        webSocketService.setConnectionStatusCallback { connected ->
            _state.value = _state.value.copy(isConnected = connected)
        }
    }

    fun setIpAddress(ip: String) {
        _state.value = _state.value.copy(ipAddress = ip)
        webSocketService.setIpAddress(ip)
    }

    fun startSession(studentId: Long) {
        viewModelScope.launch {
            try {
                delay(500)
                val connected = webSocketService.connect(_state.value.ipAddress)
                _state.value = _state.value.copy(isConnected = connected)

                if (connected) {
                    webSocketService.sendCommand("MODE:PRACTICE")
                    webSocketService.sendCommand("EMOTION:?")
                }

                val sessionId = sessionRepository.insertSession(
                    Session(studentId = studentId, mode = SessionMode.PRACTICE)
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
                webSocketService.sendCommand("MODE:PRACTICE")
                webSocketService.sendCommand("EMOTION:?")
                _state.value = _state.value.copy(toastMessage = "Connected to ESP32")
            } else {
                _state.value = _state.value.copy(toastMessage = "Failed to connect")
            }
        }
    }

    fun startStreaming() {
        if (streamJob?.isActive == true) return
        streamJob = viewModelScope.launch {
            _state.value = _state.value.copy(isStreaming = true, canSubmitFeedback = true)
        }
    }

    fun stopStreaming() {
        streamJob?.cancel()
        webSocketService.sendCommand("MODE:IDLE")
        _state.value = _state.value.copy(isStreaming = false)
    }

    fun sendFeedback(feedback: String) {
        webSocketService.sendCommand("FEEDBACK:$feedback")
        _state.value = _state.value.copy(canSubmitFeedback = false)
        viewModelScope.launch {
            delay(2000)
            _state.value = _state.value.copy(canSubmitFeedback = true)
        }
    }

    fun sendShowAnswer() {
        webSocketService.sendCommand("FEEDBACK:SHOW_ANSWER")
    }

    fun sendLEDCommand(command: String) {
        when (command) {
            "ON" -> webSocketService.sendCommand("LED:ON")
            "OFF" -> webSocketService.sendCommand("LED:OFF")
        }
    }

    fun endSession(status: SessionStatus = SessionStatus.COMPLETED) {
        viewModelScope.launch {
            _state.value.currentSession?.let { session ->
                val duration = (System.currentTimeMillis() - session.startTime) / 1000
                val emotionCount = emotionLogRepository.getEmotionCountForSession(session.id)

                sessionRepository.updateSession(
                    session.copy(
                        endTime = System.currentTimeMillis(),
                        durationSeconds = duration,
                        totalEmotionsDetected = emotionCount,
                        status = status
                    )
                )

                emotionLogJob?.cancel()
                stopStreaming()
                webSocketService.disconnect()
                _state.value = _state.value.copy(currentSession = null, shouldNavigateBack = true)
            }
        }
    }

    fun clearToast() {
        _state.value = _state.value.copy(toastMessage = null)
    }

    fun onNavigationHandled() {
        _state.value = _state.value.copy(shouldNavigateBack = false)
    }

    private fun handleMessage(message: String) {
        Log.d("PracticeViewModel", "Received: $message")
        if (message.startsWith("ERROR:")) {
            _state.value = _state.value.copy(error = message)
        }
    }

    override fun onCleared() {
        super.onCleared()
        emotionLogJob?.cancel()
        stopStreaming()
        webSocketService.disconnect()

        // Unbind network when ViewModel is destroyed
        appContext?.let { context ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                NetworkBindingHelper.unbindNetwork(context)
            }
        }
    }
}

