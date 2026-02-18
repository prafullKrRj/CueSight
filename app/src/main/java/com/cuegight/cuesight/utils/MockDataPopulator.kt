package com.cuegight.cuesight.utils

import com.cuegight.cuesight.data.model.Student
import com.cuegight.cuesight.data.repository.StudentRepository
import com.cuegight.cuesight.feature.practice.data.entity.PracticeGuess
import com.cuegight.cuesight.feature.practice.data.entity.PracticeSession
import com.cuegight.cuesight.feature.practice.data.repository.PracticeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.*

class MockDataPopulator(
    private val studentRepository: StudentRepository,
    private val practiceRepository: PracticeRepository
) {
    
    suspend fun populateMockData() = withContext(Dispatchers.IO) {
        // 1. Create/Get John student - FIXED: Use first() instead of collect
        val existingStudents = studentRepository.getAllStudents().first()
        val john = existingStudents.find { it.name == "John" }

        val johnId = john?.id ?: studentRepository.insertStudent(
            Student(
                name = "John",
                age = 8,
                diagnosis = "Autism Spectrum Disorder",
                notes = "Mock test data - Shows progressive improvement in emotion recognition",
                photoUri = null
            )
        )

        // 2. Generate sessions data
        val emotions = listOf("Happy", "Sad", "Angry", "Surprise", "Neutral")
        val baseTime = System.currentTimeMillis() - (15 * 24 * 60 * 60 * 1000L)

        val progressionData = listOf(
            1 to Pair(3, 10), 2 to Pair(3, 10), 3 to Pair(4, 10),
            4 to Pair(4, 10), 5 to Pair(5, 10), 6 to Pair(5, 12),
            7 to Pair(6, 12), 8 to Pair(7, 12), 9 to Pair(8, 12),
            10 to Pair(9, 12), 11 to Pair(10, 12), 12 to Pair(9, 12),
            13 to Pair(10, 12), 14 to Pair(11, 13), 15 to Pair(11, 13)
        )

        // OPTIMIZED: Batch prepare all data first, then insert in bulk
        val allSessions = mutableListOf<PracticeSession>()
        val allGuesses = mutableListOf<PracticeGuess>()

        progressionData.forEachIndexed { index, (_, stats) ->
            val (correctCount, totalGuesses) = stats
            val sessionStartTime = baseTime + (index * 24 * 60 * 60 * 1000L)
            val sessionEndTime = sessionStartTime + (20 * 60 * 1000L)
            val sessionId = "MOCK_SESSION_${UUID.randomUUID()}"

            allSessions.add(
                PracticeSession(
                    sessionId = sessionId,
                    studentId = johnId,
                    startTime = sessionStartTime,
                    endTime = sessionEndTime,
                    totalGuesses = totalGuesses,
                    correctGuesses = correctCount,
                    sessionAccuracy = correctCount.toFloat() / totalGuesses
                )
            )

            allGuesses.addAll(
                createGuessesForSession(sessionId, sessionStartTime, emotions, totalGuesses, correctCount)
            )
        }

        // FAST: Bulk insert
        practiceRepository.insertSessions(allSessions)
        practiceRepository.insertGuesses(allGuesses)
    }

    private fun createGuessesForSession(
        sessionId: String,
        startTime: Long,
        emotions: List<String>,
        totalGuesses: Int,
        correctCount: Int
    ): List<PracticeGuess> {
        val random = Random()
        val guesses = mutableListOf<PracticeGuess>()
        var correctSoFar = 0

        repeat(totalGuesses) { guessIndex ->
            val teacherEmotion = emotions[random.nextInt(emotions.size)]
            val shouldBeCorrect = correctSoFar < correctCount &&
                (guessIndex >= totalGuesses - (correctCount - correctSoFar) || random.nextBoolean())

            val studentGuess = if (shouldBeCorrect) {
                correctSoFar++
                teacherEmotion
            } else {
                when (teacherEmotion) {
                    "Happy" -> if (random.nextBoolean()) "Surprise" else "Neutral"
                    "Sad" -> if (random.nextBoolean()) "Angry" else "Neutral"
                    "Angry" -> if (random.nextBoolean()) "Sad" else "Surprise"
                    "Surprise" -> if (random.nextBoolean()) "Happy" else "Neutral"
                    else -> emotions[random.nextInt(emotions.size)]
                }
            }

            guesses.add(
                PracticeGuess(
                    sessionId = sessionId,
                    timestamp = startTime + (guessIndex * 90_000L),
                    teacherEmotion = teacherEmotion,
                    userGuess = studentGuess,
                    isCorrect = shouldBeCorrect,
                    responseTimeMs = (3500L + random.nextInt(2500) - 1000).coerceAtLeast(1500L)
                )
            )
        }

        return guesses
    }
}
