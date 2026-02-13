package com.cuegight.cuesight.feature.practice.domain.model

data class FeedbackState(
    val isCorrect: Boolean,
    val teacherEmotion: String,
    val userGuess: String,
    val message: String
)

