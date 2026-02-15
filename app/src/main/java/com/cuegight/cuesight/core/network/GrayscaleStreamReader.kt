package com.cuegight.cuesight.core.network

import android.graphics.Bitmap
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Reads raw grayscale stream from ESP32-CAM GC2145
 * Parses multipart stream and converts grayscale data to Bitmap
 */
class GrayscaleStreamReader(private val streamUrl: String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS) // No timeout for streaming
        .build()

    @Volatile
    private var isRunning = false

    fun startStream(onFrameReceived: (Bitmap) -> Unit, onError: (String) -> Unit) {
        if (isRunning) {
            Log.w("GrayscaleStream", "Stream already running")
            return
        }

        isRunning = true
        Thread {
            val request = Request.Builder().url(streamUrl).build()

            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        onError("HTTP error: ${response.code}")
                        return@use
                    }

                    val source = response.body?.source()
                    if (source == null) {
                        onError("Empty response body")
                        return@use
                    }

                    val boundary = "--fb0d5a5b5e6b"
                    var buffer = ByteArray(0)
                    var width = 160
                    var height = 120

                    Log.d("GrayscaleStream", "Stream started")

                    while (isRunning && !source.exhausted()) {
                        val chunk = source.readByteArray(8192)
                        buffer += chunk

                        // Extract frames from buffer
                        while (true) {
                            val text = String(buffer, Charsets.UTF_8)
                            val boundaryIdx = text.indexOf(boundary)
                            if (boundaryIdx == -1) break

                            val headerEnd = text.indexOf("\r\n\r\n", boundaryIdx)
                            if (headerEnd == -1) break

                            // Parse headers
                            val headers = text.substring(boundaryIdx, headerEnd)
                            val lengthMatch = Regex("Content-Length:\\s*(\\d+)").find(headers)
                            val widthMatch = Regex("X-Width:\\s*(\\d+)").find(headers)
                            val heightMatch = Regex("X-Height:\\s*(\\d+)").find(headers)

                            if (lengthMatch == null) break

                            val contentLength = lengthMatch.groupValues[1].toInt()
                            val dataStart =
                                text.substring(0, headerEnd + 4).toByteArray(Charsets.UTF_8).size
                            val frameEnd = dataStart + contentLength

                            if (buffer.size < frameEnd) break // Wait for complete frame

                            // Extract frame data
                            val frameData = buffer.sliceArray(dataStart until frameEnd)

                            // Update dimensions if provided
                            widthMatch?.let { width = it.groupValues[1].toInt() }
                            heightMatch?.let { height = it.groupValues[1].toInt() }

                            // Convert grayscale to bitmap
                            if (frameData.size == width * height) {
                                val bitmap = grayscaleToBitmap(frameData, width, height)
                                if (bitmap != null) {
                                    onFrameReceived(bitmap)
                                }
                            }

                            // Remove processed data from buffer
                            buffer = buffer.sliceArray(frameEnd until buffer.size)
                        }
                    }

                    Log.d("GrayscaleStream", "Stream ended")
                }
            } catch (e: IOException) {
                Log.e("GrayscaleStream", "Stream error: ${e.message}")
                onError("Stream error: ${e.message}")
            } finally {
                isRunning = false
            }
        }.start()
    }

    fun stopStream() {
        isRunning = false
    }

    private fun grayscaleToBitmap(data: ByteArray, width: Int, height: Int): Bitmap? {
        return try {
            val pixels = IntArray(width * height)

            // Convert grayscale to ARGB
            for (i in data.indices) {
                val gray = data[i].toInt() and 0xFF
                pixels[i] = (0xFF shl 24) or (gray shl 16) or (gray shl 8) or gray
            }

            Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        } catch (e: Exception) {
            Log.e("GrayscaleStream", "Bitmap conversion error: ${e.message}")
            null
        }
    }
}

