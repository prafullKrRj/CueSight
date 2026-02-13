package com.cuegight.cuesight.feature.practice.domain.model

data class MasteryResult(
    val perEmotionScores: Map<String, Float>,
    val aggregateMastery: Float,
    val weakestEmotion: String,
    val strongestEmotion: String,
    val perEmotionLambda: Map<String, Float>
)

