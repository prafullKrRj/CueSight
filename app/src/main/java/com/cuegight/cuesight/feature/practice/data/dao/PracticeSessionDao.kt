package com.cuegight.cuesight.feature.practice.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.cuegight.cuesight.feature.practice.data.entity.PracticeSession

@Dao
interface PracticeSessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: PracticeSession)

    @Update
    suspend fun update(session: PracticeSession)

    @Query("SELECT * FROM practice_sessions WHERE sessionId = :sessionId")
    suspend fun getById(sessionId: String): PracticeSession?

    @Query("SELECT * FROM practice_sessions ORDER BY startTime ASC")
    suspend fun getAll(): List<PracticeSession>

    @Query("SELECT sessionAccuracy FROM practice_sessions WHERE totalGuesses > 0 ORDER BY startTime ASC")
    suspend fun getSessionAccuracies(): List<Float>
    
    @Query("SELECT * FROM practice_sessions WHERE studentId = :studentId ORDER BY startTime ASC")
    suspend fun getByStudentId(studentId: Long): List<PracticeSession>
    
    @Query("SELECT sessionAccuracy FROM practice_sessions WHERE studentId = :studentId AND totalGuesses > 0 ORDER BY startTime ASC")
    suspend fun getSessionAccuraciesByStudent(studentId: Long): List<Float>
    
    @Query("SELECT COUNT(*) FROM practice_sessions WHERE studentId = :studentId")
    suspend fun getSessionCountByStudent(studentId: Long): Int




    @Insert(onConflict = OnConflictStrategy.REPLACE)

    suspend fun insertAll(sessions: List<PracticeSession>)
}

