package com.cuegight.cuesight.feature.practice.domain.engine

import com.cuegight.cuesight.feature.practice.data.entity.EmotionMastery
import com.cuegight.cuesight.feature.practice.data.repository.PracticeRepository
import com.cuegight.cuesight.feature.practice.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.exp
import kotlin.math.sqrt

class ErpfEngine(
    private val repository: PracticeRepository
) {

    /**
     * Metric 1: ERPI (Emotion Recognition Progress Index)
     * Computes learning trajectory using linear regression on session accuracies
     */
    suspend fun computeErpi(): ErpiResult = withContext(Dispatchers.Default) {
        val accuracies = repository.getSessionAccuracies()
        val sessionCount = accuracies.size

        if (sessionCount < 2) {
            return@withContext ErpiResult(
                slope = 0f,
                erpiScore = 0f,
                interpretation = "Need at least 2 sessions",
                sessionCount = sessionCount,
                sessionAccuracies = accuracies
            )
        }

        // Linear regression: y = mx + b
        val n = sessionCount
        val x = (1..n).map { it.toFloat() }
        val y = accuracies

        val sumX = x.sum()
        val sumY = y.sum()
        val sumXY = x.zip(y).sumOf { pair -> (pair.first * pair.second).toDouble() }.toFloat()
        val sumX2 = x.sumOf { (it * it).toDouble() }.toFloat()

        val slope = (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX)

        // Calculate standard deviation
        val mean = sumY / n
        val variance = y.sumOf { (it - mean) * (it - mean).toDouble() }.toFloat() / n
        val stdDev = sqrt(variance)

        // ERPI score
        val erpiScore = slope / (stdDev + 0.001f)

        val interpretation = when {
            erpiScore > 0.3f -> "Strong Improvement"
            erpiScore > 0.0f -> "Gradual Improvement"
            erpiScore > -0.1f -> "Stagnant"
            else -> "Declining"
        }

        ErpiResult(slope, erpiScore, interpretation, sessionCount, accuracies)
    }

    /**
     * Metric 2: Per-Emotion Mastery Score
     * Computes mastery using exponential decay based on time since last correct answer
     */
    suspend fun computeMastery(): MasteryResult = withContext(Dispatchers.Default) {
        var masteryRecords = repository.getAllMastery()

        if (masteryRecords.isEmpty()) {
            repository.initializeMasteryIfEmpty()
            masteryRecords = repository.getAllMastery()
        }

        val currentTime = System.currentTimeMillis()
        val emotionScores = mutableMapOf<String, Float>()
        val emotionLambdas = mutableMapOf<String, Float>()

        for (mastery in masteryRecords) {
            val score = if (mastery.lastCorrectTimestamp == 0L) {
                0.0f
            } else {
                val hoursSinceLastCorrect = (currentTime - mastery.lastCorrectTimestamp) / 3600000.0
                exp(-mastery.lambda * hoursSinceLastCorrect / 24.0).toFloat().coerceIn(0.0f, 1.0f)
            }

            emotionScores[mastery.emotion] = score
            emotionLambdas[mastery.emotion] = mastery.lambda
        }

        val aggregateMastery = if (emotionScores.isNotEmpty()) {
            emotionScores.values.average().toFloat()
        } else {
            0.0f
        }

        val weakest = emotionScores.minByOrNull { it.value }?.key ?: ""
        val strongest = emotionScores.maxByOrNull { it.value }?.key ?: ""

        MasteryResult(emotionScores, aggregateMastery, weakest, strongest, emotionLambdas)
    }

    /**
     * Metric 3: Confusion-Weighted Accuracy (CWA)
     * Computes weighted accuracy using confusion matrix and therapist-defined weights
     */
    suspend fun computeCwa(): CwaResult = withContext(Dispatchers.Default) {
        val guesses = repository.getAllGuesses()
        var weights = repository.getWeights()

        if (weights.isEmpty()) {
            repository.initializeWeightsIfEmpty()
            weights = repository.getWeights()
        }

        val emotions = Emotion.all().map { it.displayName }
        val confusionMatrix = mutableMapOf<String, MutableMap<String, Int>>()

        emotions.forEach { actual ->
            confusionMatrix[actual] = mutableMapOf()
            emotions.forEach { predicted ->
                confusionMatrix[actual]!![predicted] = 0
            }
        }

        for (guess in guesses) {
            val actual = guess.teacherEmotion
            val predicted = guess.userGuess

            if (confusionMatrix.containsKey(actual) && confusionMatrix[actual]!!.containsKey(predicted)) {
                confusionMatrix[actual]!![predicted] = confusionMatrix[actual]!![predicted]!! + 1
            }
        }

        val perClassRecall = mutableMapOf<String, Float>()

        emotions.forEach { emotion ->
            val tp = confusionMatrix[emotion]?.get(emotion) ?: 0
            val totalForEmotion = confusionMatrix[emotion]?.values?.sum() ?: 0
            val fn = totalForEmotion - tp

            val recall = if ((tp + fn) > 0) {
                tp.toFloat() / (tp + fn).toFloat()
            } else {
                0f
            }

            perClassRecall[emotion] = recall
        }

        val weightMap = weights.associate { it.emotion to it.weight }

        var cwaScore = 0f
        emotions.forEach { emotion ->
            val weight = weightMap[emotion] ?: 0.2f
            val recall = perClassRecall[emotion] ?: 0f
            cwaScore += weight * recall
        }

        val finalConfusionMatrix = confusionMatrix.mapValues { it.value.toMap() }

        CwaResult(cwaScore, finalConfusionMatrix, perClassRecall, weightMap)
    }

    /**
     * Update mastery lambda after a guess
     */
    suspend fun updateMasteryAfterGuess(teacherEmotion: String, isCorrect: Boolean) = withContext(Dispatchers.IO) {
        var mastery = repository.getMastery(teacherEmotion)

        if (mastery == null) {
            // Try initializing if missing
            repository.initializeMasteryIfEmpty()
            mastery = repository.getMastery(teacherEmotion)

            // If still null, create explicitly
            if (mastery == null) {
                mastery = EmotionMastery(teacherEmotion, 1.0f, 0L, 0, 0)
                repository.updateMastery(mastery)
            }
        }

        // Safe check in case persistence failed
        if (mastery != null) {
            val currentMastery = mastery
            val newLambda = if (isCorrect) {
                currentMastery.lambda * 0.85f
            } else {
                currentMastery.lambda * 1.30f
            }.coerceIn(0.1f, 5.0f)

            val updatedMastery = currentMastery.copy(
                totalAttempts = currentMastery.totalAttempts + 1,
                correctAttempts = if (isCorrect) currentMastery.correctAttempts + 1 else currentMastery.correctAttempts,
                lambda = newLambda,
                lastCorrectTimestamp = if (isCorrect) System.currentTimeMillis() else currentMastery.lastCorrectTimestamp
            )

            repository.updateMastery(updatedMastery)
        }
    }
}
