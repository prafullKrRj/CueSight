package com.cuegight.cuesight.core.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Service to send HTTP commands to ESP32
 * Command URL: http://192.168.4.1:81/cmd?v=X
 */
class HttpCommandSender {
    companion object {
        private const val TAG = "HttpCommandSender"
        private const val DEFAULT_ESP32_IP = "192.168.4.1"
        private const val COMMAND_PORT = 81
        private const val CONNECT_TIMEOUT_SECONDS = 5L
        private const val READ_TIMEOUT_SECONDS = 5L
        private const val MAX_RETRIES = 3
        private const val RETRY_DELAY_MS = 500L
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    /**
     * Send command to ESP32 with retry logic
     * @param command Command code to send
     * @param ipAddress ESP32 IP address
     * @param retries Number of retry attempts (default: MAX_RETRIES)
     * @return true if command sent successfully
     */
    suspend fun sendCommand(
        command: Int,
        ipAddress: String = DEFAULT_ESP32_IP,
        retries: Int = MAX_RETRIES
    ): Boolean {
        return withContext(Dispatchers.IO) {
            var attempt = 0
            var lastException: Exception? = null

            while (attempt < retries) {
                try {
                    val url = "http://$ipAddress:$COMMAND_PORT/cmd?v=$command"
                    val request = Request.Builder()
                        .url(url)
                        .build()

                    val response = client.newCall(request).execute()
                    val success = response.isSuccessful
                    response.close()

                    if (success) {
                        Log.d(TAG, "✅ Command sent successfully: v=$command (attempt ${attempt + 1})")
                        return@withContext true
                    } else {
                        Log.w(TAG, "⚠️ Command failed with code ${response.code} (attempt ${attempt + 1})")
                        lastException = IOException("HTTP ${response.code}")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ Command send error (attempt ${attempt + 1}): ${e.message}")
                    lastException = e
                }

                attempt++
                if (attempt < retries) {
                    kotlinx.coroutines.delay(RETRY_DELAY_MS)
                }
            }

            Log.e(TAG, "❌ Failed to send command after $retries attempts: v=$command")
            false
        }
    }

    /**
     * Send emotion command to ESP32
     * Emotion codes:
     * 1 = Happy
     * 2 = Sad
     * 3 = Angry
     * 4 = Surprise
     * 5 = Neutral
     * 6 = Disgust
     * 7 = Fear
     */
    suspend fun sendEmotion(emotion: String, ipAddress: String = DEFAULT_ESP32_IP): Boolean {
        val code = when (emotion) {
            "Happy" -> 1
            "Sad" -> 2
            "Angry" -> 3
            "Surprise", "Surprised" -> 4
            "Neutral" -> 5
            "Disgust" -> 6
            "Fear" -> 7
            else -> {
                Log.w(TAG, "⚠️ Unknown emotion: $emotion, using Neutral")
                5
            }
        }
        return sendCommand(code, ipAddress)
    }

    /**
     * Send practice mode command to ESP32
     * Practice codes:
     * 10 = Question mark (?)
     * 11 = Correct (✓)
     * 12 = Wrong (✗)
     */
    suspend fun sendPracticeCommand(
        commandType: PracticeCommandType,
        ipAddress: String = DEFAULT_ESP32_IP
    ): Boolean {
        val code = when (commandType) {
            PracticeCommandType.QUESTION -> 10
            PracticeCommandType.CORRECT -> 11
            PracticeCommandType.WRONG -> 12
        }
        return sendCommand(code, ipAddress)
    }
}

enum class PracticeCommandType {
    QUESTION,  // Show "?" on OLED
    CORRECT,   // Show "✓" on OLED
    WRONG      // Show "✗" on OLED
}
