package com.cuegight.cuesight.data.database

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromSessionMode(mode: SessionMode): String {
        return mode.name
    }
    
    @TypeConverter
    fun toSessionMode(value: String): SessionMode {
        return SessionMode.valueOf(value)
    }


}
enum class SessionMode {
    IDLE, TEACHING, PRACTICE
};