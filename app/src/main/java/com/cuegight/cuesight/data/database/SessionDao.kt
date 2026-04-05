package com.cuegight.cuesight.data.database

import androidx.room.*
import com.cuegight.cuesight.data.model.Session
import com.cuegight.cuesight.data.model.SessionMode
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions ORDER BY startTime DESC")
    fun getAllSessions(): Flow<List<Session>>
    
    @Query("SELECT * FROM sessions WHERE studentId = :studentId ORDER BY startTime DESC")
    fun getSessionsByStudent(studentId: Long): Flow<List<Session>>
    
    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun getSessionById(id: Long): Session?
    
    @Query("SELECT * FROM sessions WHERE id = :id")
    fun getSessionByIdFlow(id: Long): Flow<Session?>
    
    @Query("SELECT * FROM sessions WHERE endTime IS NULL LIMIT 1")
    suspend fun getActiveSession(): Session?
    
    @Query("SELECT * FROM sessions WHERE endTime IS NULL LIMIT 1")
    fun getActiveSessionFlow(): Flow<Session?>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: Session): Long
    
    @Update
    suspend fun updateSession(session: Session)
    
    @Delete
    suspend fun deleteSession(session: Session)
    
    @Query("SELECT COUNT(*) FROM sessions WHERE studentId = :studentId")
    suspend fun getSessionCountForStudent(studentId: Long): Int
    
    @Query("SELECT COUNT(*) FROM sessions WHERE studentId = :studentId AND mode = :mode")
    suspend fun getSessionCountByMode(studentId: Long, mode: SessionMode): Int
    
    @Query("SELECT SUM(durationSeconds) FROM sessions WHERE studentId = :studentId")
    suspend fun getTotalDurationForStudent(studentId: Long): Long?
}
