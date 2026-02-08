package com.cuegight.cuesight.data.database

import androidx.room.*
import com.cuegight.cuesight.data.model.AccuracyPoint
import com.cuegight.cuesight.data.model.ConfusionPair
import com.cuegight.cuesight.data.model.EmotionAccuracy
import com.cuegight.cuesight.data.model.EmotionFrequency
import com.cuegight.cuesight.data.model.EmotionLog
import kotlinx.coroutines.flow.Flow

@Dao
interface EmotionLogDao {
    @Query("SELECT * FROM emotion_logs WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getEmotionsBySession(sessionId: Long): Flow<List<EmotionLog>>
    
    @Query("SELECT * FROM emotion_logs WHERE sessionId = :sessionId ORDER BY timestamp DESC")
    suspend fun getEmotionsBySessionSync(sessionId: Long): List<EmotionLog>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEmotion(emotion: EmotionLog): Long
    
    @Query("SELECT COUNT(*) FROM emotion_logs WHERE sessionId = :sessionId")
    suspend fun getEmotionCountForSession(sessionId: Long): Int
    
    @Query("SELECT COUNT(*) FROM emotion_logs WHERE sessionId = :sessionId AND emotion = :emotionType")
    suspend fun getEmotionCountByType(sessionId: Long, emotionType: String): Int
    
    @Query("""
        SELECT emotion, COUNT(*) as count 
        FROM emotion_logs 
        WHERE sessionId = :sessionId 
        GROUP BY emotion 
        ORDER BY count DESC
    """)
    suspend fun getEmotionFrequency(sessionId: Long): List<EmotionFrequency>

    @Query("""
        SELECT emotion AS aiDetected, studentGuess, COUNT(*) as count
        FROM emotion_logs
        INNER JOIN sessions ON emotion_logs.sessionId = sessions.id
        WHERE sessions.studentId = :studentId
          AND studentGuess IS NOT NULL
        GROUP BY emotion, studentGuess
        ORDER BY count DESC
    """)
    fun getConfusionMatrixData(studentId: Long): Flow<List<ConfusionPair>>

    @Query("""
        SELECT sessionId,
               MIN(timestamp) as date,
               (SUM(CASE WHEN isCorrect THEN 1 ELSE 0 END) * 1.0 / COUNT(*)) as accuracy
        FROM emotion_logs
        INNER JOIN sessions ON emotion_logs.sessionId = sessions.id
        WHERE sessions.studentId = :studentId
          AND isCorrect IS NOT NULL
        GROUP BY sessionId
        ORDER BY date ASC
    """)
    fun getAccuracyOverTime(studentId: Long): Flow<List<AccuracyPoint>>

    @Query("""
        SELECT emotion,
               COUNT(*) as totalCount,
               SUM(CASE WHEN isCorrect THEN 1 ELSE 0 END) as correctCount,
               (SUM(CASE WHEN isCorrect THEN 1 ELSE 0 END) * 1.0 / COUNT(*)) as accuracy
        FROM emotion_logs
        INNER JOIN sessions ON emotion_logs.sessionId = sessions.id
        WHERE sessions.studentId = :studentId
          AND isCorrect IS NOT NULL
        GROUP BY emotion
        ORDER BY totalCount DESC
    """)
    fun getEmotionAccuracy(studentId: Long): Flow<List<EmotionAccuracy>>
    
    @Delete
    suspend fun deleteEmotion(emotion: EmotionLog)
    
    @Query("DELETE FROM emotion_logs WHERE sessionId = :sessionId")
    suspend fun deleteEmotionsBySession(sessionId: Long)
}
