package com.cuegight.cuesight.core.network

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit

/**
 * Service to parse MJPEG stream from ESP32 camera
 * Stream URL: http://192.168.4.1/stream
 */
class HttpMjpegStreamService {
    companion object {
        private const val TAG = "HttpMjpegStream"
        private const val DEFAULT_ESP32_IP = "192.168.4.1"
        private const val STREAM_PORT = 80
        private const val BOUNDARY = "--fb"
        private const val CONNECT_TIMEOUT_SECONDS = 10L
        private const val READ_TIMEOUT_SECONDS = 30L
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    /**
     * Opens MJPEG stream and emits frames as Bitmaps
     * @param ipAddress ESP32 IP address
     * @return Flow of Bitmap frames
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
                throw IOException("Failed to connect: ${response.code}")
            }

            val inputStream = response.body?.byteStream()
                ?: throw IOException("No response body")

            Log.d(TAG, "✅ Connected to MJPEG stream")
            
            // Parse multipart stream
            parseMultipartStream(inputStream) { frameBytes ->
                // Decode JPEG to Bitmap
                val bitmap = BitmapFactory.decodeByteArray(frameBytes, 0, frameBytes.size)
                if (bitmap != null) {
                    emit(bitmap)
                } else {
                    Log.w(TAG, "⚠️ Failed to decode frame")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Stream error: ${e.message}", e)
            throw e
        }
    }

    /**
     * Parses MJPEG multipart stream
     * Format:
     * --boundary
     * Content-Type: image/jpeg
     * Content-Length: XXXX
     * 
     * [JPEG data]
     */
    private suspend fun parseMultipartStream(
        inputStream: InputStream,
        onFrame: suspend (ByteArray) -> Unit
    ) {
        withContext(Dispatchers.IO) {
            val buffer = ByteArray(8192)
            val frameBuffer = ByteArrayOutputStream()
            var inFrame = false
            var contentLength = -1
            var bytesRead = 0

            try {
                while (true) {
                    val read = inputStream.read(buffer)
                    if (read == -1) break

                    for (i in 0 until read) {
                        val byte = buffer[i]

                        if (!inFrame) {
                            // Look for boundary and Content-Length header
                            frameBuffer.write(byte.toInt())
                            val line = frameBuffer.toString()

                            if (line.contains("Content-Length:")) {
                                val lengthStr = line.substringAfter("Content-Length:").trim()
                                contentLength = lengthStr.substringBefore("\r").substringBefore("\n").trim().toIntOrNull() ?: -1
                            }

                            // Start of JPEG data (after blank line following headers)
                            if (line.endsWith("\r\n\r\n") && contentLength > 0) {
                                frameBuffer.reset()
                                inFrame = true
                                bytesRead = 0
                            }
                        } else {
                            // Reading JPEG frame data
                            frameBuffer.write(byte.toInt())
                            bytesRead++

                            if (bytesRead >= contentLength) {
                                // Frame complete
                                val frameData = frameBuffer.toByteArray()
                                onFrame(frameData)
                                
                                frameBuffer.reset()
                                inFrame = false
                                contentLength = -1
                                bytesRead = 0
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error parsing stream: ${e.message}", e)
                throw e
            }
        }
    }

    /**
     * Test connection to ESP32
     * @param ipAddress ESP32 IP address
     * @return true if connection successful
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
