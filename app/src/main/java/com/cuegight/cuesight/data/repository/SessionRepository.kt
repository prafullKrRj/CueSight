package com.cuegight.cuesight.data.repository

import com.cuegight.cuesight.data.database.CueSightDatabase
import com.cuegight.cuesight.data.model.Session
import com.cuegight.cuesight.data.model.SessionMode
import kotlinx.coroutines.flow.Flow

class SessionRepository(private val database: CueSightDatabase) {
    private val sessionDao = database.sessionDao()
    
    fun getAllSessions(): Flow<List<Session>> = sessionDao.getAllSessions()
    
    fun getSessionsByStudent(studentId: Long): Flow<List<Session>> = 
        sessionDao.getSessionsByStudent(studentId)
    
    suspend fun getSessionById(id: Long): Session? = sessionDao.getSessionById(id)
    
    fun getSessionByIdFlow(id: Long): Flow<Session?> = sessionDao.getSessionByIdFlow(id)
    
    suspend fun getActiveSession(): Session? = sessionDao.getActiveSession()
    
    fun getActiveSessionFlow(): Flow<Session?> = sessionDao.getActiveSessionFlow()
    
    suspend fun insertSession(session: Session): Long = sessionDao.insertSession(session)
    
    suspend fun updateSession(session: Session) = sessionDao.updateSession(session)
    
    suspend fun deleteSession(session: Session) = sessionDao.deleteSession(session)
    
    suspend fun getSessionCountForStudent(studentId: Long): Int = 
        sessionDao.getSessionCountForStudent(studentId)
    
    suspend fun getSessionCountByMode(studentId: Long, mode: SessionMode): Int = 
        sessionDao.getSessionCountByMode(studentId, mode)
    
    suspend fun getTotalDurationForStudent(studentId: Long): Long = 
        sessionDao.getTotalDurationForStudent(studentId) ?: 0L
}
