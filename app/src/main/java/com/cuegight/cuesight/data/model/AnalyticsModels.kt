package com.cuegight.cuesight.data.model

data class ConfusionPair(
    val aiDetected: String,
    val studentGuess: String,
    val count: Int
)

data class AccuracyPoint(
    val sessionId: Long,
    val date: Long,
    val accuracy: Float
)

data class EmotionAccuracy(
    val emotion: String,
    val totalCount: Int,
    val correctCount: Int,
    val accuracy: Float
)
