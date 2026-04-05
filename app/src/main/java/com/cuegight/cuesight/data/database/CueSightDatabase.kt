package com.cuegight.cuesight.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.cuegight.cuesight.data.model.EmotionLog
import com.cuegight.cuesight.data.model.Session
import com.cuegight.cuesight.data.model.Student
import com.cuegight.cuesight.feature.practice.data.dao.*
import com.cuegight.cuesight.feature.practice.data.entity.*

@Database(
    entities = [
        Student::class,
        Session::class,
        EmotionLog::class,
        PracticeGuess::class,
        PracticeSession::class,
        EmotionMastery::class,
        TherapistWeight::class
    ],
    version = 6,
    exportSchema = false
)
abstract class CueSightDatabase : RoomDatabase() {
    abstract fun studentDao(): StudentDao
    abstract fun sessionDao(): SessionDao
    abstract fun emotionLogDao(): EmotionLogDao

    // Practice Mode DAOs
    abstract fun practiceGuessDao(): PracticeGuessDao
    abstract fun practiceSessionDao(): PracticeSessionDao
    abstract fun emotionMasteryDao(): EmotionMasteryDao
    abstract fun therapistWeightDao(): TherapistWeightDao

    companion object {
        @Volatile
        private var INSTANCE: CueSightDatabase? = null
        
        fun getDatabase(context: Context): CueSightDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    CueSightDatabase::class.java,
                    "cuesight_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
