package com.cuegight.cuesight.service

import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.net.InetAddress
import java.util.concurrent.TimeUnit

class WebSocketService {
    private companion object {
        private const val TAG = "WebSocketService"
        private const val DEFAULT_ESP32_IP = "192.168.4.1"
        private const val PORT = 8888
        private const val MAX_CONNECTION_ATTEMPTS = 2
        private const val CONNECTION_RETRY_DELAY_MS = 500L
    }

    private val client = OkHttpClient.Builder()
        .retryOnConnectionFailure(true)
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()
    private var webSocket: WebSocket? = null
    private var ipAddress: String = DEFAULT_ESP32_IP
    private val connectionMutex = Mutex()

    private var frameCallback: ((ByteArray) -> Unit)? = null
    private var messageCallback: ((String) -> Unit)? = null
    private var connectionStatusCallback: ((Boolean) -> Unit)? = null

    fun setIpAddress(ip: String) {
        ipAddress = ip
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

    suspend fun connect(ip: String): Boolean {
        ipAddress = ip.trim()
        return withContext(Dispatchers.IO) {
            connectionMutex.withLock {
                try {
                    if (ipAddress.isBlank()) {
                        Log.e(TAG, "Cannot connect: empty IP address")
                        return@withLock false
                    }
                    if (!isValidIpv4(ipAddress)) {
                        Log.e(TAG, "Cannot connect: invalid IP address format")
                        return@withLock false
                    }

                    Log.d(TAG, "Connecting to $ipAddress:$PORT")

                    // Best-effort reachability check (some devices/networks block ICMP probes).
                    try {
                        val reachable = InetAddress.getByName(ipAddress).isReachable(3000)
                        if (!reachable) {
                            Log.w(TAG, "IP unreachable via probe, attempting WebSocket connection")
                        } else {
                            Log.d(TAG, "ESP32 IP is reachable")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Reachability probe failed, continuing: ${e.message}")
                    }

                    val wsUrl = "ws://$ipAddress:$PORT"
                    val request = Request.Builder().url(wsUrl).build()

                    repeat(MAX_CONNECTION_ATTEMPTS) { attempt ->
                        val connectionResult = CompletableDeferred<Boolean>()

                        webSocket?.cancel()
                        webSocket = null
                        webSocket = client.newWebSocket(request, createListener(connectionResult))

                        val result = withTimeoutOrNull(10_000L) {
                            connectionResult.await()
                        } ?: false

                        Log.d(TAG, "Connection attempt ${attempt + 1} result: $result")
                        if (result) return@withLock true

                        webSocket?.cancel()
                        webSocket = null
                        if (attempt < MAX_CONNECTION_ATTEMPTS - 1) delay(CONNECTION_RETRY_DELAY_MS)
                    }

                    false
                } catch (e: Exception) {
                    Log.e(TAG, "Connection error: ${e.message}", e)
                    false
                }
            }
        }
    }

    fun sendCommand(command: String) {
        val socket = webSocket
        if (socket == null) {
            Log.w(TAG, "Cannot send command, socket is null")
            return
        }
        val success = socket.send(command)
        if (success) {
            Log.d(TAG, "Sent command: $command")
        } else {
            Log.e(TAG, "Failed to send command: $command")
        }
    }

    fun disconnect() {
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
        connectionStatusCallback?.invoke(false)
        Log.d(TAG, "Disconnected")
    }

    private fun createListener(connectionResult: CompletableDeferred<Boolean>): WebSocketListener {
        return object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                synchronized(this@WebSocketService) {
                    this@WebSocketService.webSocket = webSocket
                }
                Log.d(TAG, "WebSocket opened")
                connectionResult.complete(true)
                connectionStatusCallback?.invoke(true)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "Received text: $text")
                messageCallback?.invoke(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                frameCallback?.invoke(bytes.toByteArray())
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${t.message}", t)
                synchronized(this@WebSocketService) {
                    if (this@WebSocketService.webSocket === webSocket) {
                        this@WebSocketService.webSocket = null
                    }
                }
                connectionStatusCallback?.invoke(false)
                if (!connectionResult.isCompleted) {
                    connectionResult.complete(false)
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $code - $reason")
                synchronized(this@WebSocketService) {
                    if (this@WebSocketService.webSocket === webSocket) {
                        this@WebSocketService.webSocket = null
                    }
                }
                connectionStatusCallback?.invoke(false)
                if (!connectionResult.isCompleted) {
                    connectionResult.complete(false)
                }
            }
        }
    }

    private fun isValidIpv4(ip: String): Boolean {
        val parts = ip.split(".")
        if (parts.size != 4) return false
        return parts.all { part ->
            part.isNotEmpty() &&
                part.length <= 3 &&
                (part == "0" || !part.startsWith("0")) &&
                part.all(Char::isDigit) &&
                part.toIntOrNull() in 0..255
        }
    }
}
