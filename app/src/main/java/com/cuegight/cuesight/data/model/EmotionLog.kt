package com.cuegight.cuesight.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
enum class FrameQuality {
    OK, NO_FACE, POOR
}

@Entity(
    tableName = "emotion_logs",
    foreignKeys = [
        ForeignKey(
            entity = Session::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)

data class EmotionLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: Long,
    val emotion: String,
    val timestamp: Long = System.currentTimeMillis(),
    val confidence: Float = 0f,
    val frameQuality: FrameQuality = FrameQuality.OK,
    val smilingProbability: Float? = null,
    val leftEyeOpenProbability: Float? = null,
    val rightEyeOpenProbability: Float? = null
)

data class EmotionFrequency(
    val emotion: String,
    val count: Int
)
