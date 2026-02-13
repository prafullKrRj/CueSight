package com.cuegight.cuesight.feature.practice.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.cuegight.cuesight.feature.practice.data.entity.TherapistWeight

@Dao
interface TherapistWeightDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(weight: TherapistWeight)

    @Update
    suspend fun update(weight: TherapistWeight)

    @Query("SELECT * FROM therapist_weights")
    suspend fun getAll(): List<TherapistWeight>

    @Query("SELECT * FROM therapist_weights WHERE emotion = :emotion")
    suspend fun getByEmotion(emotion: String): TherapistWeight?
}

