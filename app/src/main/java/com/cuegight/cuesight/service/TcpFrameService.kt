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
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.InetAddress
import java.net.SocketTimeoutException
import javax.net.SocketFactory



class TcpFrameService {
    private companion object {
        private const val TAG = "TcpFrameService"
        private const val DEFAULT_ESP32_IP = "192.168.4.1"
        private const val PORT = 81
        private const val CONNECT_TIMEOUT_MS = 5_000
        private const val READ_TIMEOUT_MS = 10_000     // <<< INCREASED from 5s to 10s
        private const val BUFFER_SIZE = 32_768
        private const val MAX_FRAME_BYTES = 100_000
        private const val MIN_RETRY_DELAY_MS = 1_000L  // <<< INCREASED from 500ms
        private const val MAX_RETRY_DELAY_MS = 5_000L  // <<< INCREASED from 3s
        private const val STATUS_CONNECTING = "STATUS:Connecting"
        private const val STATUS_STREAMING = "STATUS:Streaming"
        private const val STATUS_RECONNECTING = "STATUS:Reconnecting"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val commandMutex = Mutex()
    private var socketFactory: SocketFactory? = null
    private var socket: Socket? = null
    private var input: DataInputStream? = null
    private var output: OutputStream? = null
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
                    if (ipAddress.isBlank() || !isValidIpv4(ipAddress)) {
                        Log.e(TAG, "Cannot connect: invalid IP address")
                        return@withLock false
                    }

                    Log.d(TAG, "Connecting to $ipAddress:$PORT (TCP)")

                    // >>> FIX: SKIP the reachability check — it wastes 3 seconds
                    // >>> and often fails on ESP32 AP mode anyway

                    notifyMessage(STATUS_CONNECTING)

                    // >>> FIX: Stop any existing reader FIRST, then close socket
                    shouldStayConnected = false
                    readerJob?.cancel()
                    readerJob = null
                    delay(100)  // let old reader die
                    closeInternal()
                    delay(100)  // let ESP32 notice disconnect

                    shouldStayConnected = true
                    val connected = openSocket()
                    if (!connected) {
                        shouldStayConnected = false
                        return@withLock false
                    }

                    // >>> FIX: Small delay to let ESP32 process the connection
                    delay(200)

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
        scope.launch(Dispatchers.IO) {
            commandMutex.withLock {
                try {
                    val out = output
                    if (out == null) {
                        Log.w(TAG, "Cannot send command, no output stream: $command")
                        return@withLock
                    }
                    out.write("$command\n".toByteArray(Charsets.UTF_8))
                    out.flush()
                    Log.d(TAG, "Sent command: $command")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to send command: $command — ${e.message}")
                    // >>> FIX: DON'T close the socket here!
                    // The reader loop will detect the broken connection
                }
            }
        }
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
            var consecutiveTimeouts = 0

            while (isActive && shouldStayConnected) {
                try {
                    if (socket == null || input == null) {
                        notifyMessage(STATUS_RECONNECTING)
                        notifyConnection(false)
                        if (!openSocket()) {
                            delay(retryDelayMs)
                            retryDelayMs = (retryDelayMs + MIN_RETRY_DELAY_MS)
                                .coerceAtMost(MAX_RETRY_DELAY_MS)
                            continue
                        }
                        retryDelayMs = MIN_RETRY_DELAY_MS
                        consecutiveTimeouts = 0
                    }

                    val stream = input ?: continue
                    val len = stream.readInt()
                    if (len <= 0 || len > MAX_FRAME_BYTES) {
                        throw IllegalStateException("Invalid frame length: $len")
                    }
                    val jpegBytes = ByteArray(len)
                    stream.readFully(jpegBytes)
                    notifyFrame(jpegBytes)

                    // Reset on successful frame
                    retryDelayMs = MIN_RETRY_DELAY_MS
                    consecutiveTimeouts = 0

                } catch (e: SocketTimeoutException) {
                    // >>> FIX: DON'T reconnect on timeout!
                    // The socket is still alive, just no data yet.
                    // This happens when STREAM:START hasn't been sent yet.
                    consecutiveTimeouts++
                    Log.w(TAG, "Read timeout #$consecutiveTimeouts (socket still alive)")

                    if (consecutiveTimeouts > 3) {
                        // Only reconnect after 3 consecutive timeouts (30 seconds)
                        Log.e(TAG, "Too many timeouts, reconnecting")
                        closeInternal()
                        notifyConnection(false)
                        notifyMessage(STATUS_RECONNECTING)
                        consecutiveTimeouts = 0
                        delay(retryDelayMs)
                    }
                    // Otherwise just loop back and try reading again

                } catch (e: Exception) {
                    if (!shouldStayConnected) break
                    Log.e(TAG, "TCP stream read failed: ${e.message}", e)
                    closeInternal()
                    notifyConnection(false)
                    notifyMessage(STATUS_RECONNECTING)
                    delay(retryDelayMs)
                    retryDelayMs = (retryDelayMs + MIN_RETRY_DELAY_MS)
                        .coerceAtMost(MAX_RETRY_DELAY_MS)
                }
            }
        }
    }

    private fun openSocket(): Boolean {
        return try {
            val newSocket = socketFactory?.createSocket() ?: Socket()
            newSocket.tcpNoDelay = true
            newSocket.soTimeout = READ_TIMEOUT_MS
            newSocket.setSendBufferSize(4096)
            newSocket.setReceiveBufferSize(BUFFER_SIZE)
            newSocket.connect(InetSocketAddress(ipAddress, PORT), CONNECT_TIMEOUT_MS)
            socket = newSocket
            input = DataInputStream(BufferedInputStream(newSocket.getInputStream(), BUFFER_SIZE))
            output = newSocket.getOutputStream()
            notifyConnection(true)
            notifyMessage(STATUS_STREAMING)
            Log.d(TAG, "Socket connected to $ipAddress:$PORT")
            true
        } catch (e: Exception) {
            Log.e(TAG, "TCP connect failed: ${e.message}", e)
            closeInternal()
            false
        }
    }

    private fun closeInternal() {
        try { input?.close() } catch (_: Exception) {}
        try { output?.close() } catch (_: Exception) {}
        try { socket?.close() } catch (_: Exception) {}
        input = null
        output = null
        socket = null
    }

    private fun notifyFrame(bytes: ByteArray) {
        scope.launch(Dispatchers.Main) {
            frameCallback?.invoke(bytes)
        }
    }

    private fun notifyMessage(message: String) {
        scope.launch(Dispatchers.Main) {
            messageCallback?.invoke(message)
        }
    }

    private fun notifyConnection(isConnected: Boolean) {
        scope.launch(Dispatchers.Main) {
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