package com.cuegight.cuesight.feature.practice.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy.Companion.REPLACE
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
    
    @Query("""
        SELECT pg.* FROM practice_guesses pg
        INNER JOIN practice_sessions ps ON pg.sessionId = ps.sessionId
        WHERE ps.studentId = :studentId
        ORDER BY pg.timestamp ASC
    """)
    suspend fun getByStudentId(studentId: Long): List<PracticeGuess>
    
    @Query("""
        SELECT pg.* FROM practice_guesses pg
        INNER JOIN practice_sessions ps ON pg.sessionId = ps.sessionId
        WHERE ps.studentId = :studentId AND pg.teacherEmotion = :emotion
        ORDER BY pg.timestamp ASC
    """)
    suspend fun getByStudentIdAndEmotion(studentId: Long, emotion: String): List<PracticeGuess>

    @Insert(onConflict = REPLACE)
    fun insertAll(guesses: List<PracticeGuess>)
}

