package com.cuegight.cuesight.data.database

import androidx.room.*
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
    
    @Delete
    suspend fun deleteEmotion(emotion: EmotionLog)
    
    @Query("DELETE FROM emotion_logs WHERE sessionId = :sessionId")
    suspend fun deleteEmotionsBySession(sessionId: Long)
}
