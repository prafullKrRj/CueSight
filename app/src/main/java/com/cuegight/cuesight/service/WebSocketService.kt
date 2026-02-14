package com.cuegight.cuesight.service

import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.net.InetAddress

class WebSocketService {
    private val client = OkHttpClient()
    private var webSocket: WebSocket? = null
    private var ipAddress: String = "192.168.4.1"
    private val port = 8888

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
        ipAddress = ip
        return withContext(Dispatchers.IO) {
            try {
                Log.d("WebSocketService", "Connecting to $ipAddress:$port")

                // Check if we can reach ESP32
                try {
                    val reachable = InetAddress.getByName(ipAddress).isReachable(3000)
                    if (!reachable) {
                        Log.e("WebSocketService", "ESP32 IP $ipAddress is NOT REACHABLE")
                        return@withContext false
                    }
                    Log.d("WebSocketService", "ESP32 IP is reachable")
                } catch (e: Exception) {
                    Log.e("WebSocketService", "Network check failed: ${e.message}")
                }

                val connectionResult = CompletableDeferred<Boolean>()
                val wsUrl = "ws://$ipAddress:$port"
                val request = Request.Builder()
                    .url(wsUrl)
                    .build()

                webSocket?.close(1000, "Reconnecting")
                webSocket = client.newWebSocket(request, createListener(connectionResult))

                val result = withTimeoutOrNull(10_000L) {
                    connectionResult.await()
                } ?: false

                Log.d("WebSocketService", "Connection result: $result")
                result
            } catch (e: Exception) {
                Log.e("WebSocketService", "Connection error: ${e.message}", e)
                false
            }
        }
    }

    fun sendCommand(command: String) {
        val socket = webSocket
        if (socket == null) {
            Log.w("WebSocketService", "Cannot send command, socket is null")
            return
        }
        val success = socket.send(command)
        if (success) {
            Log.d("WebSocketService", "Sent command: $command")
        } else {
            Log.e("WebSocketService", "Failed to send command: $command")
        }
    }

    fun disconnect() {
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
        connectionStatusCallback?.invoke(false)
        Log.d("WebSocketService", "Disconnected")
    }

    private fun createListener(connectionResult: CompletableDeferred<Boolean>): WebSocketListener {
        return object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("WebSocketService", "WebSocket opened")
                connectionResult.complete(true)
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
                Log.e("WebSocketService", "WebSocket failure: ${t.message}", t)
                connectionStatusCallback?.invoke(false)
                if (!connectionResult.isCompleted) {
                    connectionResult.complete(false)
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("WebSocketService", "WebSocket closed: $code - $reason")
                connectionStatusCallback?.invoke(false)
                if (!connectionResult.isCompleted) {
                    connectionResult.complete(false)
                }
            }
        }
    }
}
