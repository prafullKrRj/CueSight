package com.cuegight.cuesight.feature.practice.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.cuegight.cuesight.feature.practice.data.entity.EmotionMastery

@Dao
interface EmotionMasteryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(mastery: EmotionMastery)

    @Update
    suspend fun update(mastery: EmotionMastery)

    @Query("SELECT * FROM emotion_mastery WHERE emotion = :emotion")
    suspend fun getByEmotion(emotion: String): EmotionMastery?

    @Query("SELECT * FROM emotion_mastery")
    suspend fun getAll(): List<EmotionMastery>
}

