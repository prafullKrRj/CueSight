package com.cuegight.cuesight.feature.practice.data.repository

import com.cuegight.cuesight.feature.practice.data.dao.PracticeGuessDao
import com.cuegight.cuesight.feature.practice.data.dao.PracticeSessionDao
import com.cuegight.cuesight.feature.practice.data.dao.EmotionMasteryDao
import com.cuegight.cuesight.feature.practice.data.dao.TherapistWeightDao
import com.cuegight.cuesight.feature.practice.data.entity.PracticeGuess
import com.cuegight.cuesight.feature.practice.data.entity.PracticeSession
import com.cuegight.cuesight.feature.practice.data.entity.EmotionMastery
import com.cuegight.cuesight.feature.practice.data.entity.TherapistWeight
import com.cuegight.cuesight.feature.practice.domain.model.Emotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import android.util.Log

class PracticeRepository(
    private val practiceGuessDao: PracticeGuessDao,
    private val practiceSessionDao: PracticeSessionDao,
    private val emotionMasteryDao: EmotionMasteryDao,
    private val therapistWeightDao: TherapistWeightDao
) {

    // Guess operations
    suspend fun insertGuess(guess: PracticeGuess) = withContext(Dispatchers.IO) {
        try {
            practiceGuessDao.insert(guess)
        } catch (e: Exception) {
            Log.w("PracticeRepository", "Failed to insert guess, retrying...", e)
            try {
                delay(500)
                practiceGuessDao.insert(guess)
            } catch (retryException: Exception) {
                Log.e("PracticeRepository", "Failed to insert guess after retry", retryException)
            }
        }
    }

    suspend fun getGuessesForSession(sessionId: String): List<PracticeGuess> = withContext(Dispatchers.IO) {
        practiceGuessDao.getBySessionId(sessionId)
    }

    suspend fun getAllGuesses(): List<PracticeGuess> = withContext(Dispatchers.IO) {
        practiceGuessDao.getAll()
    }

    suspend fun getGuessesByEmotion(emotion: String): List<PracticeGuess> = withContext(Dispatchers.IO) {
        practiceGuessDao.getByTeacherEmotion(emotion)
    }

    suspend fun pruneOldGuesses(daysToKeep: Int = 30) = withContext(Dispatchers.IO) {
        val cutoffTimestamp = System.currentTimeMillis() - (daysToKeep * 86400000L)
        practiceGuessDao.deleteOlderThan(cutoffTimestamp)
    }

    // Session operations
    suspend fun insertSession(session: PracticeSession) = withContext(Dispatchers.IO) {
        try {
            practiceSessionDao.insert(session)
        } catch (e: Exception) {
            Log.w("PracticeRepository", "Failed to insert session, retrying...", e)
            try {
                delay(500)
                practiceSessionDao.insert(session)
            } catch (retryException: Exception) {
                Log.e("PracticeRepository", "Failed to insert session after retry", retryException)
            }
        }
    }

    suspend fun updateSession(session: PracticeSession) = withContext(Dispatchers.IO) {
        try {
            practiceSessionDao.update(session)
        } catch (e: Exception) {
            Log.w("PracticeRepository", "Failed to update session, retrying...", e)
            try {
                delay(500)
                practiceSessionDao.update(session)
            } catch (retryException: Exception) {
                Log.e("PracticeRepository", "Failed to update session after retry", retryException)
            }
        }
    }

    suspend fun getSession(sessionId: String): PracticeSession? = withContext(Dispatchers.IO) {
        practiceSessionDao.getById(sessionId)
    }

    suspend fun getAllSessions(): List<PracticeSession> = withContext(Dispatchers.IO) {
        practiceSessionDao.getAll()
    }

    suspend fun getSessionAccuracies(): List<Float> = withContext(Dispatchers.IO) {
        practiceSessionDao.getSessionAccuracies()
    }

    // Mastery operations
    suspend fun getMastery(emotion: String): EmotionMastery? = withContext(Dispatchers.IO) {
        emotionMasteryDao.getByEmotion(emotion)
    }

    suspend fun getAllMastery(): List<EmotionMastery> = withContext(Dispatchers.IO) {
        emotionMasteryDao.getAll()
    }

    suspend fun updateMastery(mastery: EmotionMastery) = withContext(Dispatchers.IO) {
        try {
            emotionMasteryDao.update(mastery)
        } catch (e: Exception) {
            Log.w("PracticeRepository", "Failed to update mastery, retrying...", e)
            try {
                delay(500)
                emotionMasteryDao.update(mastery)
            } catch (retryException: Exception) {
                Log.e("PracticeRepository", "Failed to update mastery after retry", retryException)
            }
        }
    }

    suspend fun initializeMasteryIfEmpty() = withContext(Dispatchers.IO) {
        val existing = emotionMasteryDao.getAll()
        if (existing.isEmpty()) {
            Emotion.all().forEach { emotion ->
                try {
                    emotionMasteryDao.insert(
                        EmotionMastery(
                            emotion = emotion.displayName,
                            lambda = 1.0f,
                            lastCorrectTimestamp = 0L,
                            totalAttempts = 0,
                            correctAttempts = 0
                        )
                    )
                } catch (e: Exception) {
                    Log.e("PracticeRepository", "Failed to initialize mastery for ${emotion.displayName}", e)
                }
            }
        }
    }

    // Weight operations
    suspend fun getWeights(): List<TherapistWeight> = withContext(Dispatchers.IO) {
        therapistWeightDao.getAll()
    }

    suspend fun getWeight(emotion: String): TherapistWeight? = withContext(Dispatchers.IO) {
        therapistWeightDao.getByEmotion(emotion)
    }

    suspend fun insertWeight(weight: TherapistWeight) = withContext(Dispatchers.IO) {
        therapistWeightDao.insert(weight)
    }

    suspend fun updateWeight(weight: TherapistWeight) = withContext(Dispatchers.IO) {
        try {
            therapistWeightDao.update(weight)
        } catch (e: Exception) {
            Log.w("PracticeRepository", "Failed to update weight, retrying...", e)
            try {
                delay(500)
                therapistWeightDao.update(weight)
            } catch (retryException: Exception) {
                Log.e("PracticeRepository", "Failed to update weight after retry", retryException)
            }
        }
    }

    suspend fun initializeWeightsIfEmpty() = withContext(Dispatchers.IO) {
        val existing = therapistWeightDao.getAll()
        if (existing.isEmpty()) {
            Emotion.all().forEach { emotion ->
                try {
                    therapistWeightDao.insert(
                        TherapistWeight(
                            emotion = emotion.displayName,
                            weight = 0.20f
                        )
                    )
                } catch (e: Exception) {
                    Log.e("PracticeRepository", "Failed to initialize weight for ${emotion.displayName}", e)
                }
            }
        }
    }

    suspend fun updateAllWeights(weights: Map<String, Float>) = withContext(Dispatchers.IO) {
        val sum = weights.values.sum()
        val normalizedWeights = if (kotlin.math.abs(sum - 1.0f) > 0.01f) {
            weights.mapValues { it.value / sum }
        } else {
            weights
        }

        normalizedWeights.forEach { (emotion, weight) ->
            try {
                therapistWeightDao.update(
                    TherapistWeight(emotion = emotion, weight = weight)
                )
            } catch (e: Exception) {
                Log.w("PracticeRepository", "Failed to update weight for $emotion, retrying...", e)
                try {
                    delay(500)
                    therapistWeightDao.update(
                        TherapistWeight(emotion = emotion, weight = weight)
                    )
                } catch (retryException: Exception) {
                    Log.e("PracticeRepository", "Failed to update weight for $emotion after retry", retryException)
                }
            }
        }
    }
}
