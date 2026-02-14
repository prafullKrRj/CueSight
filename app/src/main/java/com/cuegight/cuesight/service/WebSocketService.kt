package com.cuegight.cuesight.service

import android.net.Network
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.InetAddress
import javax.net.SocketFactory

class WebSocketService {
    private companion object {
        private const val TAG = "WebSocketService"
        private const val DEFAULT_ESP32_IP = "192.168.4.1"
        private const val PORT = 81
        private const val CONNECT_TIMEOUT_MS = 3_000
        private const val READ_TIMEOUT_MS = 5_000
        private const val BUFFER_SIZE = 32_768
        private const val MAX_FRAME_BYTES = 100_000
        private const val MIN_RETRY_DELAY_MS = 500L
        private const val MAX_RETRY_DELAY_MS = 3_000L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var socketFactory: SocketFactory? = null
    private var socket: Socket? = null
    private var input: DataInputStream? = null
    private var readerJob: Job? = null
    private var shouldStayConnected = false
    private var ipAddress: String = DEFAULT_ESP32_IP
    private val connectionMutex = Mutex()

    private var frameCallback: ((ByteArray) -> Unit)? = null
    private var messageCallback: ((String) -> Unit)? = null
    private var connectionStatusCallback: ((Boolean) -> Unit)? = null

    fun setIpAddress(ip: String) {
        ipAddress = ip
    }

    /**
     * CRITICAL FIX FOR "ENETUNREACH":
     * Binds socket creation to a specific Android network interface.
     * This forces traffic to use WiFi even when it has no internet.
     * Must be called after connecting to ESP32 WiFi AP.
     */
    fun bindToNetwork(network: Network) {
        Log.d(TAG, "Binding socket factory to specific network: $network")
        socketFactory = network.socketFactory
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

                    Log.d(TAG, "Connecting to $ipAddress:$PORT (TCP)")

                    // Best-effort reachability check (some devices/networks block ICMP probes).
                    try {
                        val reachable = InetAddress.getByName(ipAddress).isReachable(3000)
                        if (!reachable) {
                            Log.w(TAG, "IP unreachable via probe, attempting TCP connection")
                        } else {
                            Log.d(TAG, "ESP32 IP is reachable")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Reachability probe failed, continuing: ${e.message}")
                    }

                    notifyMessage("STATUS:Connecting")
                    shouldStayConnected = true
                    closeInternal()
                    val connected = openSocket()
                    if (!connected) {
                        shouldStayConnected = false
                        return@withLock false
                    }

                    startReaderLoop()
                    true
                } catch (e: Exception) {
                    Log.e(TAG, "Connection error: ${e.message}", e)
                    false
                }
            }
        }
    }

    fun sendCommand(command: String) {
        Log.d(TAG, "Ignoring command for TCP frame stream: $command")
    }

    fun disconnect() {
        shouldStayConnected = false
        readerJob?.cancel()
        readerJob = null
        closeInternal()
        notifyConnection(false)
        Log.d(TAG, "Disconnected")
    }

    private fun startReaderLoop() {
        readerJob?.cancel()
        readerJob = scope.launch(Dispatchers.IO) {
            var retryDelayMs = MIN_RETRY_DELAY_MS
            while (isActive && shouldStayConnected) {
                try {
                    if (socket == null || input == null) {
                        notifyMessage("STATUS:Reconnecting")
                        notifyConnection(false)
                        if (!openSocket()) {
                            delay(retryDelayMs)
                            retryDelayMs = (retryDelayMs + MIN_RETRY_DELAY_MS).coerceAtMost(MAX_RETRY_DELAY_MS)
                            continue
                        }
                        retryDelayMs = MIN_RETRY_DELAY_MS
                    }

                    val stream = input ?: continue
                    val len = stream.readInt()
                    if (len <= 0 || len > MAX_FRAME_BYTES) {
                        throw IllegalStateException("Invalid frame length: $len")
                    }
                    val jpegBytes = ByteArray(len)
                    stream.readFully(jpegBytes)
                    notifyFrame(jpegBytes)
                } catch (e: Exception) {
                    if (!shouldStayConnected) break
                    Log.e(TAG, "TCP stream read failed: ${e.message}", e)
                    closeInternal()
                    notifyConnection(false)
                    notifyMessage("STATUS:Reconnecting")
                    delay(retryDelayMs)
                    retryDelayMs = (retryDelayMs + MIN_RETRY_DELAY_MS).coerceAtMost(MAX_RETRY_DELAY_MS)
                }
            }
        }
    }

    private fun openSocket(): Boolean {
        return try {
            val newSocket = socketFactory?.createSocket() as? Socket ?: Socket()
            newSocket.tcpNoDelay = true
            newSocket.soTimeout = READ_TIMEOUT_MS
            newSocket.connect(InetSocketAddress(ipAddress, PORT), CONNECT_TIMEOUT_MS)
            socket = newSocket
            input = DataInputStream(BufferedInputStream(newSocket.getInputStream(), BUFFER_SIZE))
            notifyConnection(true)
            notifyMessage("STATUS:Streaming")
            true
        } catch (e: Exception) {
            Log.e(TAG, "TCP connect failed: ${e.message}", e)
            closeInternal()
            false
        }
    }

    private fun closeInternal() {
        try {
            input?.close()
        } catch (_: Exception) {
        }
        try {
            socket?.close()
        } catch (_: Exception) {
        }
        input = null
        socket = null
    }

    private fun notifyFrame(bytes: ByteArray) {
        scope.launch {
            frameCallback?.invoke(bytes)
        }
    }

    private fun notifyMessage(message: String) {
        scope.launch {
            messageCallback?.invoke(message)
        }
    }

    private fun notifyConnection(isConnected: Boolean) {
        scope.launch {
            connectionStatusCallback?.invoke(isConnected)
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
