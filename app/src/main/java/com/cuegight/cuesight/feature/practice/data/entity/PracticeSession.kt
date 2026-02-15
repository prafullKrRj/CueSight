package com.cuegight.cuesight.feature.practice.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "practice_sessions")
data class PracticeSession(
    @PrimaryKey
    val sessionId: String,
    val studentId: Long = 0L, // Link to student - default 0 for backward compatibility
    val startTime: Long,
    val endTime: Long? = null,
    val totalGuesses: Int = 0,
    val correctGuesses: Int = 0,
    val sessionAccuracy: Float = 0f
)

