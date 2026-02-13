package com.cuegight.cuesight.feature.practice.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "emotion_mastery")
data class EmotionMastery(
    @PrimaryKey
    val emotion: String,
    val lambda: Float = 1.0f,
    val lastCorrectTimestamp: Long = 0L,
    val totalAttempts: Int = 0,
    val correctAttempts: Int = 0
)

