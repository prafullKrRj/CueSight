package com.cuegight.cuesight.data.repository

import com.cuegight.cuesight.data.database.CueSightDatabase
import com.cuegight.cuesight.data.model.AccuracyPoint
import com.cuegight.cuesight.data.model.ConfusionPair
import com.cuegight.cuesight.data.model.EmotionAccuracy
import com.cuegight.cuesight.data.model.EmotionFrequency
import com.cuegight.cuesight.data.model.EmotionLog
import kotlinx.coroutines.flow.Flow

class EmotionLogRepository(private val database: CueSightDatabase) {
    private val emotionLogDao = database.emotionLogDao()
    
    fun getEmotionsBySession(sessionId: Long): Flow<List<EmotionLog>> = 
        emotionLogDao.getEmotionsBySession(sessionId)
    
    suspend fun getEmotionsBySessionSync(sessionId: Long): List<EmotionLog> = 
        emotionLogDao.getEmotionsBySessionSync(sessionId)
    
    suspend fun insertEmotion(emotion: EmotionLog): Long = emotionLogDao.insertEmotion(emotion)
    
    suspend fun getEmotionCountForSession(sessionId: Long): Int = 
        emotionLogDao.getEmotionCountForSession(sessionId)
    
    suspend fun getEmotionCountByType(sessionId: Long, emotionType: String): Int = 
        emotionLogDao.getEmotionCountByType(sessionId, emotionType)
    
    suspend fun getEmotionFrequency(sessionId: Long): List<EmotionFrequency> =
        emotionLogDao.getEmotionFrequency(sessionId)

    fun getConfusionMatrixData(studentId: Long): Flow<List<ConfusionPair>> =
        emotionLogDao.getConfusionMatrixData(studentId)

    fun getAccuracyOverTime(studentId: Long): Flow<List<AccuracyPoint>> =
        emotionLogDao.getAccuracyOverTime(studentId)

    fun getEmotionAccuracy(studentId: Long): Flow<List<EmotionAccuracy>> =
        emotionLogDao.getEmotionAccuracy(studentId)
    
    suspend fun deleteEmotion(emotion: EmotionLog) = emotionLogDao.deleteEmotion(emotion)
    
    suspend fun deleteEmotionsBySession(sessionId: Long) = 
        emotionLogDao.deleteEmotionsBySession(sessionId)
}
