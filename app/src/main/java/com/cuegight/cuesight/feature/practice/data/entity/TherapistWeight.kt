package com.cuegight.cuesight.feature.practice.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "therapist_weights")
data class TherapistWeight(
    @PrimaryKey
    val emotion: String,
    val weight: Float = 0.20f
)

