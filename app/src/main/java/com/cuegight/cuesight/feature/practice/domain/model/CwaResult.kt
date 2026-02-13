package com.cuegight.cuesight.feature.practice.domain.model

data class CwaResult(
    val cwaScore: Float,
    val confusionMatrix: Map<String, Map<String, Int>>,
    val perClassRecall: Map<String, Float>,
    val weights: Map<String, Float>
)

