package com.cuegight.cuesight.feature.connection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cuegight.cuesight.core.network.ConnectionManager
import com.cuegight.cuesight.core.network.ConnectionResult
import com.cuegight.cuesight.core.network.HttpMjpegStreamService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for Connection Flow
 * Handles WiFi connection and HTTP connection testing
 */
class ConnectionViewModel(
    private val connectionManager: ConnectionManager,
    private val streamService: HttpMjpegStreamService
) : ViewModel() {

    private val _state = MutableStateFlow(ConnectionState())
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    init {
        checkConnection()
    }

    fun checkConnection() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            
            val isWifiEnabled = connectionManager.isWifiEnabled()
            val isConnectedToESP32 = connectionManager.isConnectedToESP32()
            
            when {
                // Already connected to ESP32_CAM - test HTTP
                isConnectedToESP32 -> {
                    _state.value = _state.value.copy(
                        isWifiEnabled = true,
                        isConnectedToESP32 = true,
                        currentStep = ConnectionStep.TEST_HTTP,
                        isLoading = false
                    )
                    testHttpConnection()
                }
                // WiFi enabled but not connected - show connect screen
                isWifiEnabled -> {
                    _state.value = _state.value.copy(
                        isWifiEnabled = true,
                        isConnectedToESP32 = false,
                        currentStep = ConnectionStep.CONNECT_WIFI,
                        isLoading = false
                    )
                }
                // WiFi disabled - show enable WiFi screen
                else -> {
                    _state.value = _state.value.copy(
                        isWifiEnabled = false,
                        isConnectedToESP32 = false,
                        currentStep = ConnectionStep.CHECK_WIFI,
                        isLoading = false
                    )
                }
            }
        }
    }

    fun connectToWiFi() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            
            when (val result = connectionManager.connectToESP32()) {
                is ConnectionResult.Success -> {
                    _state.value = _state.value.copy(
                        isConnectedToESP32 = true,
                        currentStep = ConnectionStep.TEST_HTTP,
                        isLoading = false,
                        needsUserAction = false
                    )
                    testHttpConnection()
                }
                is ConnectionResult.NeedsUserAction -> {
                    _state.value = _state.value.copy(
                        needsUserAction = true,
                        userActionMessage = result.message,
                        isLoading = false
                    )
                }
                is ConnectionResult.Error -> {
                    _state.value = _state.value.copy(
                        error = result.message,
                        isLoading = false
                    )
                }
            }
        }
    }

    fun openWifiSettings() {
        connectionManager.openWifiSettings()
    }

    fun retryAfterSettings() {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                isLoading = true,
                needsUserAction = false,
                userActionMessage = null,
                error = null
            )
            
            // Give Android a moment to finish connecting
            kotlinx.coroutines.delay(1000)

            // Check if now connected to ESP32_CAM
            val isConnectedToESP32 = connectionManager.isConnectedToESP32()
            val currentSsid = connectionManager.getCurrentSSID()

            if (isConnectedToESP32) {
                // Connected! Move to HTTP test
                _state.value = _state.value.copy(
                    isConnectedToESP32 = true,
                    currentStep = ConnectionStep.TEST_HTTP,
                    isLoading = false
                )
                testHttpConnection()
            } else {
                // Still not connected - show helpful error
                val errorMsg = if (currentSsid.isNullOrBlank()) {
                    "Not connected to any WiFi. Please connect to 'ESP32_CAM_P' and try again."
                } else {
                    "Currently connected to '$currentSsid'. Please connect to 'ESP32_CAM_P' and try again."
                }

                _state.value = _state.value.copy(
                    error = errorMsg,
                    isLoading = false,
                    // Keep user on CONNECT_WIFI step so they can try again
                    currentStep = ConnectionStep.CONNECT_WIFI,
                    needsUserAction = true
                )
            }
        }
    }

    fun testHttpConnection() {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                isLoading = true,
                currentStep = ConnectionStep.TEST_HTTP,
                error = null
            )
            
            val isConnected = streamService.testConnection()
            
            if (isConnected) {
                _state.value = _state.value.copy(
                    isHttpConnected = true,
                    currentStep = ConnectionStep.SUCCESS,
                    isLoading = false
                )
                
                // Auto-navigate after showing success
                kotlinx.coroutines.delay(1500)
                _state.value = _state.value.copy(shouldNavigateToHome = true)
            } else {
                _state.value = _state.value.copy(
                    error = "Failed to connect to ESP32 camera. Make sure the device is powered on.",
                    isLoading = false
                )
            }
        }
    }

    fun retry() {
        _state.value = ConnectionState()
        checkConnection()
    }

    fun onNavigated() {
        _state.value = _state.value.copy(shouldNavigateToHome = false)
    }
}

data class ConnectionState(
    val currentStep: ConnectionStep = ConnectionStep.CHECK_WIFI,
    val isLoading: Boolean = false,
    val error: String? = null,
    val isWifiEnabled: Boolean = false,
    val isConnectedToESP32: Boolean = false,
    val isHttpConnected: Boolean = false,
    val needsUserAction: Boolean = false,
    val userActionMessage: String? = null,
    val shouldNavigateToHome: Boolean = false
)

enum class ConnectionStep {
    CHECK_WIFI,     // Step 1: Check WiFi status
    CONNECT_WIFI,   // Step 2: Connect to ESP32_CAM
    TEST_HTTP,      // Step 3: Test HTTP connection
    SUCCESS         // Step 4: Success animation
}
