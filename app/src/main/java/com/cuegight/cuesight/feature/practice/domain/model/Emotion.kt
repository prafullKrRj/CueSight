package com.cuegight.cuesight.feature.practice.domain.model

enum class Emotion(val displayName: String) {
    HAPPY("Happy"),
    SAD("Sad"),
    ANGRY("Angry"),
    SURPRISED("Surprised"),
    NEUTRAL("Neutral");

    companion object {
        fun fromString(value: String): Emotion {
            return values().find { it.displayName.equals(value, ignoreCase = true) }
                ?: throw IllegalArgumentException("Unknown emotion: $value")
        }

        fun all(): List<Emotion> {
            return values().toList()
        }

        fun getAllEmotions(): List<String> {
            return values().map { it.displayName }
        }
    }
}

