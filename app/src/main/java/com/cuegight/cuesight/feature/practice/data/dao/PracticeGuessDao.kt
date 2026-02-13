package com.cuegight.cuesight.feature.practice.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.cuegight.cuesight.feature.practice.data.entity.PracticeGuess

@Dao
interface PracticeGuessDao {

    @Insert
    suspend fun insert(guess: PracticeGuess)

    @Query("SELECT * FROM practice_guesses WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getBySessionId(sessionId: String): List<PracticeGuess>

    @Query("SELECT * FROM practice_guesses ORDER BY timestamp DESC")
    suspend fun getAll(): List<PracticeGuess>

    @Query("SELECT * FROM practice_guesses WHERE teacherEmotion = :emotion ORDER BY timestamp DESC")
    suspend fun getByTeacherEmotion(emotion: String): List<PracticeGuess>

    @Query("DELETE FROM practice_guesses WHERE timestamp < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long)

    @Query("SELECT COUNT(*) FROM practice_guesses")
    suspend fun countAll(): Int
}

