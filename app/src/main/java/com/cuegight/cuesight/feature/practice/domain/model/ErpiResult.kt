package com.cuegight.cuesight.feature.practice.domain.model

data class ErpiResult(
    val slope: Float,
    val erpiScore: Float,
    val interpretation: String,
    val sessionCount: Int,
    val sessionAccuracies: List<Float>
)

