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
        private const val FRAME_PORT = 81
        private const val COMMAND_PORT = 82
        private const val CONNECT_TIMEOUT_MS = 5_000
        private const val READ_TIMEOUT_MS = 10_000
        private const val BUFFER_SIZE = 32_768
        private const val MAX_FRAME_BYTES = 100_000
        private const val MIN_RETRY_DELAY_MS = 1_000L
        private const val MAX_RETRY_DELAY_MS = 5_000L
        private const val STATUS_CONNECTING = "STATUS:Connecting"
        private const val STATUS_STREAMING = "STATUS:Streaming"
        private const val STATUS_RECONNECTING = "STATUS:Reconnecting"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val commandMutex = Mutex()
    private var socketFactory: SocketFactory? = null
    
    // Two separate sockets
    private var frameSocket: Socket? = null
    private var commandSocket: Socket? = null
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
        Log.d(TAG, "✅ Binding socket factory to specific network: $network")
        socketFactory = network.socketFactory
        Log.d(TAG, "✅ Socket factory bound successfully")
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

                    Log.d(TAG, "🔌 Connecting to $ipAddress (Frame:$FRAME_PORT, Command:$COMMAND_PORT)")
                    Log.d(TAG, "🔌 Socket factory bound: ${socketFactory != null}")

                    notifyMessage(STATUS_CONNECTING)

                    // Stop any existing reader FIRST, then close sockets
                    shouldStayConnected = false
                    readerJob?.cancel()
                    readerJob = null
                    delay(100)  // let old reader die
                    closeInternal()
                    delay(100)  // let ESP32 notice disconnect

                    shouldStayConnected = true
                    val connected = openSockets()
                    if (!connected) {
                        shouldStayConnected = false
                        return@withLock false
                    }

                    // Small delay to let ESP32 process the connections
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
                        Log.w(TAG, "⚠️ Cannot send command, no output stream: $command")
                        return@withLock
                    }
                    out.write("$command\n".toByteArray(Charsets.UTF_8))
                    out.flush()
                    Log.d(TAG, "📤 Sent command: $command")
                } catch (e: Exception) {
                    Log.w(TAG, "❌ Failed to send command: $command — ${e.message}")
                    // Don't close the socket here - let reader loop detect failure
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
            var totalFramesReceived = 0

            Log.d(TAG, "📺 Frame reader loop started")
            
            while (isActive && shouldStayConnected) {
                try {
                    if (frameSocket == null || input == null) {
                        Log.w(TAG, "⚠️ Frame socket not connected, attempting reconnect...")
                        notifyMessage(STATUS_RECONNECTING)
                        notifyConnection(false)
                        if (!openSockets()) {
                            Log.e(TAG, "❌ Reconnect failed, retrying in ${retryDelayMs}ms...")
                            delay(retryDelayMs)
                            retryDelayMs = (retryDelayMs + MIN_RETRY_DELAY_MS)
                                .coerceAtMost(MAX_RETRY_DELAY_MS)
                            continue
                        }
                        retryDelayMs = MIN_RETRY_DELAY_MS
                        consecutiveTimeouts = 0
                    }

                    val stream = input ?: continue
                    
                    // Read 4-byte big-endian frame length
                    val len = stream.readInt()
                    if (len <= 0 || len > MAX_FRAME_BYTES) {
                        throw IllegalStateException("Invalid frame length: $len")
                    }
                    
                    // Read JPEG frame data
                    val jpegBytes = ByteArray(len)
                    stream.readFully(jpegBytes)
                    
                    totalFramesReceived++
                    if (totalFramesReceived % 50 == 0) {
                        Log.d(TAG, "📺 Received $totalFramesReceived frames (latest: ${len} bytes)")
                    }
                    
                    notifyFrame(jpegBytes)

                    // Reset on successful frame
                    retryDelayMs = MIN_RETRY_DELAY_MS
                    consecutiveTimeouts = 0

                } catch (e: SocketTimeoutException) {
                    // Don't reconnect on timeout - socket is still alive
                    consecutiveTimeouts++
                    if (consecutiveTimeouts <= 3) {
                        Log.w(TAG, "⏱️ Read timeout #$consecutiveTimeouts (socket still alive, waiting for frames...)")
                    }

                    if (consecutiveTimeouts > 3) {
                        // Only reconnect after 3 consecutive timeouts (30 seconds)
                        Log.e(TAG, "❌ Too many timeouts, reconnecting both sockets")
                        closeInternal()
                        notifyConnection(false)
                        notifyMessage(STATUS_RECONNECTING)
                        consecutiveTimeouts = 0
                        delay(retryDelayMs)
                    }

                } catch (e: Exception) {
                    if (!shouldStayConnected) break
                    Log.e(TAG, "❌ TCP stream read failed: ${e.message}", e)
                    closeInternal()
                    notifyConnection(false)
                    notifyMessage(STATUS_RECONNECTING)
                    delay(retryDelayMs)
                    retryDelayMs = (retryDelayMs + MIN_RETRY_DELAY_MS)
                        .coerceAtMost(MAX_RETRY_DELAY_MS)
                }
            }
            
            Log.d(TAG, "📺 Frame reader loop stopped (total frames received: $totalFramesReceived)")
        }
    }

    private fun openSockets(): Boolean {
        return try {
            Log.d(TAG, "🔌 Opening sockets...")
            Log.d(TAG, "🔌 SocketFactory available: ${socketFactory != null}")
            
            if (socketFactory == null) {
                Log.w(TAG, "⚠️ WARNING: No socket factory bound! Connection may fail if device has mobile data.")
                Log.w(TAG, "⚠️ Make sure NetworkBindingHelper.bindToWiFiNetwork() was called BEFORE connect().")
            }
            
            // Open frame socket (port 81, read-only)
            Log.d(TAG, "🔌 Creating frame socket for port $FRAME_PORT...")
            val newFrameSocket = socketFactory?.createSocket() ?: Socket()
            newFrameSocket.tcpNoDelay = true
            newFrameSocket.soTimeout = READ_TIMEOUT_MS
            newFrameSocket.setReceiveBufferSize(BUFFER_SIZE)
            Log.d(TAG, "🔌 Connecting frame socket to $ipAddress:$FRAME_PORT...")
            newFrameSocket.connect(InetSocketAddress(ipAddress, FRAME_PORT), CONNECT_TIMEOUT_MS)
            frameSocket = newFrameSocket
            input = DataInputStream(BufferedInputStream(newFrameSocket.getInputStream(), BUFFER_SIZE))
            Log.d(TAG, "✅ Frame socket connected to $ipAddress:$FRAME_PORT")

            // Small delay to let ESP32 process the first connection
            Thread.sleep(100)

            // Open command socket (port 82, write-only)
            Log.d(TAG, "🔌 Creating command socket for port $COMMAND_PORT...")
            val newCommandSocket = socketFactory?.createSocket() ?: Socket()
            newCommandSocket.tcpNoDelay = true
            // No read timeout needed for write-only socket
            newCommandSocket.setSendBufferSize(4096)
            Log.d(TAG, "🔌 Connecting command socket to $ipAddress:$COMMAND_PORT...")
            newCommandSocket.connect(InetSocketAddress(ipAddress, COMMAND_PORT), CONNECT_TIMEOUT_MS)
            commandSocket = newCommandSocket
            output = newCommandSocket.getOutputStream()
            Log.d(TAG, "✅ Command socket connected to $ipAddress:$COMMAND_PORT")

            notifyConnection(true)
            notifyMessage(STATUS_STREAMING)
            Log.d(TAG, "✅ Both sockets connected successfully!")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ TCP connect failed: ${e.message}", e)
            Log.e(TAG, "❌ Stack trace: ", e)
            closeInternal()
            false
        }
    }

    private fun closeInternal() {
        try { input?.close() } catch (_: Exception) {}
        try { output?.close() } catch (_: Exception) {}
        try { frameSocket?.close() } catch (_: Exception) {}
        try { commandSocket?.close() } catch (_: Exception) {}
        input = null
        output = null
        frameSocket = null
        commandSocket = null
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