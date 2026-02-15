package com.cuegight.cuesight.feature.practice.domain.model

data class ErpiResult(
    val erpiScore: Float,
    val slope: Float,
    val interpretation: String,
    val sessionCount: Int,
    val sessionAccuracies: List<Float>
)
