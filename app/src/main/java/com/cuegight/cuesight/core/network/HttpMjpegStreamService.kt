package com.cuegight.cuesight.core.network

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/**
 * Service to parse MJPEG stream from ESP32 camera
 * Stream URL: http://192.168.4.1/stream
 */
class HttpMjpegStreamService {
    companion object {
        private const val TAG = "HttpMjpegStream"
        private const val DEFAULT_ESP32_IP = "192.168.4.1"
        private const val STREAM_PORT = 80
        private const val CONNECT_TIMEOUT_SECONDS = 10L
        private const val READ_TIMEOUT_SECONDS = 30L

        // JPEG markers
        private const val JPEG_START_MARKER_1: Byte = 0xFF.toByte()
        private const val JPEG_START_MARKER_2: Byte = 0xD8.toByte()
        private const val JPEG_END_MARKER_1: Byte = 0xFF.toByte()
        private const val JPEG_END_MARKER_2: Byte = 0xD9.toByte()
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    /**
     * Opens MJPEG stream and emits frames as Bitmaps
     * Uses JPEG marker detection (0xFFD8...0xFFD9) instead of boundary parsing
     */
    fun streamFrames(ipAddress: String = DEFAULT_ESP32_IP): Flow<Bitmap> = flow {
        val url = "http://$ipAddress:$STREAM_PORT/stream"
        Log.d(TAG, "📺 Connecting to MJPEG stream: $url")

        val request = Request.Builder()
            .url(url)
            .build()

        try {
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                Log.e(TAG, "❌ Failed to connect to stream: ${response.code}")
                throw Exception("Failed to connect: ${response.code}")
            }

            val inputStream = response.body?.byteStream()
                ?: throw Exception("No response body")

            Log.d(TAG, "✅ Connected to MJPEG stream, starting to parse frames...")

            val bufferedStream = BufferedInputStream(inputStream, 8192)
            val frameBuffer = ByteArrayOutputStream(32768)
            var frameCount = 0
            var inFrame = false
            var prevByte: Byte = 0

            while (coroutineContext.isActive) {
                val byte = bufferedStream.read()
                if (byte == -1) {
                    Log.w(TAG, "⚠️ Stream ended")
                    break
                }

                val currentByte = byte.toByte()

                // Detect JPEG start marker (0xFF 0xD8)
                if (prevByte == JPEG_START_MARKER_1 && currentByte == JPEG_START_MARKER_2) {
                    if (inFrame) {
                        // Found start of new frame while already in frame - reset
                        frameBuffer.reset()
                    }
                    inFrame = true
                    frameBuffer.write(JPEG_START_MARKER_1.toInt())
                    frameBuffer.write(JPEG_START_MARKER_2.toInt())
                }
                // Detect JPEG end marker (0xFF 0xD9)
                else if (prevByte == JPEG_END_MARKER_1 && currentByte == JPEG_END_MARKER_2 && inFrame) {
                    frameBuffer.write(JPEG_END_MARKER_2.toInt())

                    // Complete frame received
                    val jpegData = frameBuffer.toByteArray()

                    try {
                        val bitmap = BitmapFactory.decodeByteArray(jpegData, 0, jpegData.size)
                        if (bitmap != null) {
                            frameCount++
                            if (frameCount % 30 == 0) {
                                Log.d(TAG, "📷 Frame $frameCount decoded (${jpegData.size} bytes, ${bitmap.width}x${bitmap.height})")
                            }
                            emit(bitmap)
                        } else {
                            Log.w(TAG, "⚠️ Failed to decode JPEG frame (${jpegData.size} bytes)")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ Error decoding frame: ${e.message}")
                    }

                    frameBuffer.reset()
                    inFrame = false
                }
                // Accumulate frame data
                else if (inFrame) {
                    frameBuffer.write(currentByte.toInt())
                }

                prevByte = currentByte
            }

            Log.d(TAG, "🛑 Stream ended, total frames: $frameCount")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Stream error: ${e.message}", e)
            throw e
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Test connection to ESP32
     */
    suspend fun testConnection(ipAddress: String = DEFAULT_ESP32_IP): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val url = "http://$ipAddress/frame"
                val request = Request.Builder()
                    .url(url)
                    .build()

                val response = client.newCall(request).execute()
                val success = response.isSuccessful
                response.close()

                if (success) {
                    Log.d(TAG, "✅ Connection test successful")
                } else {
                    Log.e(TAG, "❌ Connection test failed: ${response.code}")
                }
                success
            } catch (e: Exception) {
                Log.e(TAG, "❌ Connection test error: ${e.message}", e)
                false
            }
        }
    }
}
