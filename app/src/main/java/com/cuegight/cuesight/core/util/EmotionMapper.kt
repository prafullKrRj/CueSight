package com.cuegight.cuesight.core.util

/**
 * Maps emotions to ESP32 command codes and visual representations
 */
object EmotionMapper {
    
    // Emotion to command code mapping
    private val emotionToCode = mapOf(
        "Happy" to 1,
        "Sad" to 2,
        "Angry" to 3,
        "Surprise" to 4,
        "Surprised" to 4,  // Alias
        "Neutral" to 5,
        "Disgust" to 6,
        "Fear" to 7
    )

    // Code to emotion mapping (reverse)
    private val codeToEmotion = emotionToCode.entries
        .associateBy({ it.value }, { it.key })
        .filterKeys { it != 4 || it == 4 }  // Keep only one "Surprise" variant

    // Emotion to emoji mapping
    private val emotionToEmoji = mapOf(
        "Happy" to "😊",
        "Sad" to "😢",
        "Angry" to "😠",
        "Surprise" to "😲",
        "Surprised" to "😲",
        "Neutral" to "😐",
        "Disgust" to "🤢",
        "Fear" to "😨"
    )

    // Practice mode command codes
    const val PRACTICE_QUESTION = 10
    const val PRACTICE_CORRECT = 11
    const val PRACTICE_WRONG = 12

    /**
     * Get command code for emotion
     */
    fun getEmotionCode(emotion: String): Int? {
        return emotionToCode[emotion]
    }

    /**
     * Get emotion from command code
     */
    fun getEmotionFromCode(code: Int): String? {
        return codeToEmotion[code]
    }

    /**
     * Get emoji for emotion
     */
    fun getEmotionEmoji(emotion: String): String {
        return emotionToEmoji[emotion] ?: "❓"
    }

    /**
     * Get all supported emotions
     */
    fun getAllEmotions(): List<String> {
        return listOf("Happy", "Sad", "Angry", "Surprise", "Neutral", "Disgust", "Fear")
    }

    /**
     * Normalize emotion name (handle aliases)
     */
    fun normalizeEmotion(emotion: String): String {
        return when (emotion.lowercase()) {
            "surprised" -> "Surprise"
            else -> emotion
        }
    }
}
