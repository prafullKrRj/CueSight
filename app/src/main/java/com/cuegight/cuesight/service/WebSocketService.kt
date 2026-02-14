package com.cuegight.cuesight.service

import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.net.InetAddress

private const val DEFAULT_ESP32_IP = "192.168.4.1"
private const val WEBSOCKET_PORT = 8888

data class WebSocketState(
    val isConnected: Boolean = false,
    val ipAddress: String = DEFAULT_ESP32_IP,
    val error: String = ""
)

/**
 * Core WebSocket service for communicating with ESP32-CAM
 * Shared by all session modes (Teaching, Practice, Test)
 */
class WebSocketService {

    private val client = OkHttpClient()
    private var webSocket: WebSocket? = null
    private var frameCallback: ((ByteArray) -> Unit)? = null
    private var messageCallback: ((String) -> Unit)? = null
    private var connectionStatusCallback: ((Boolean) -> Unit)? = null

    private val _state = MutableStateFlow(WebSocketState())
    val state: StateFlow<WebSocketState> = _state.asStateFlow()

    fun setIpAddress(ip: String) {
        _state.value = _state.value.copy(ipAddress = ip)
    }

    fun setFrameCallback(callback: (ByteArray) -> Unit) {
        frameCallback = callback
    }

    fun setMessageCallback(callback: (String) -> Unit) {
        messageCallback = callback
    }

    fun setConnectionStatusCallback(callback: (Boolean) -> Unit) {
        connectionStatusCallback = callback
    }

    suspend fun connect(ipAddress: String = _state.value.ipAddress): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d("WebSocketService", "Connecting to $ipAddress:$WEBSOCKET_PORT")

                // Check if ESP32 is reachable
                try {
                    val reachable = InetAddress.getByName(ipAddress).isReachable(3000)
                    if (!reachable) {
                        Log.e("WebSocketService", "ESP32 IP $ipAddress is NOT REACHABLE")
                        _state.value = _state.value.copy(
                            error = "Cannot reach ESP32. Connect to ESP32 WiFi first!"
                        )
                        return@withContext false
                    }
                    Log.d("WebSocketService", "ESP32 IP is reachable")
                } catch (e: Exception) {
                    Log.e("WebSocketService", "Network check failed: ${e.message}")
                }

                val connectionResult = CompletableDeferred<Boolean>()
                val wsUrl = "ws://$ipAddress:$WEBSOCKET_PORT"
                val request = Request.Builder()
                    .url(wsUrl)
                    .build()

                webSocket?.close(1000, "Reconnecting")
                webSocket = client.newWebSocket(request, createWebSocketListener(connectionResult))

                val result = withTimeoutOrNull(10_000L) {
                    connectionResult.await()
                } ?: false

                Log.d("WebSocketService", "Connection completed: $result")
                result
            } catch (e: Exception) {
                Log.e("WebSocketService", "Connection error: ${e.message}", e)
                _state.value = _state.value.copy(error = e.message ?: "Unknown error")
                false
            }
        }
    }

    private fun createWebSocketListener(connectionResult: CompletableDeferred<Boolean>): WebSocketListener {
        return object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("WebSocketService", "WebSocket OPENED")
                connectionResult.complete(true)
                _state.value = _state.value.copy(isConnected = true, error = "")
                connectionStatusCallback?.invoke(true)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d("WebSocketService", "Received text: $text")
                messageCallback?.invoke(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                frameCallback?.invoke(bytes.toByteArray())
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("WebSocketService", "WebSocket FAILURE: ${t.message}", t)
                _state.value = _state.value.copy(
                    isConnected = false,
                    error = t.message ?: "Connection failed"
                )
                if (!connectionResult.isCompleted) {
                    connectionResult.complete(false)
                }
                connectionStatusCallback?.invoke(false)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("WebSocketService", "WebSocket CLOSED - Code: $code, Reason: $reason")
                _state.value = _state.value.copy(isConnected = false)
                if (!connectionResult.isCompleted) {
                    connectionResult.complete(false)
                }
                connectionStatusCallback?.invoke(false)
            }
        }
    }

    fun sendCommand(command: String): Boolean {
        val socket = webSocket ?: return false
        val result = socket.send(command)
        if (!result) {
            Log.w("WebSocketService", "Failed to send: $command")
        } else {
            Log.d("WebSocketService", "Sent: $command")
        }
        return result
    }

    fun disconnect() {
        webSocket?.close(1000, "Disconnected")
        webSocket = null
        _state.value = _state.value.copy(isConnected = false)
    }

    fun isConnected(): Boolean = _state.value.isConnected
}

