package com.cuegight.cuesight.feature.practice.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "practice_guesses")
data class PracticeGuess(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val sessionId: String,
    val timestamp: Long,
    val teacherEmotion: String,
    val userGuess: String,
    val isCorrect: Boolean,
    val responseTimeMs: Long
)