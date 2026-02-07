package com.cuegight.cuesight.data.database

import androidx.room.TypeConverter
import com.cuegight.cuesight.data.model.FrameQuality
import com.cuegight.cuesight.data.model.SessionMode
import com.cuegight.cuesight.data.model.SessionStatus

class Converters {
    @TypeConverter
    fun fromSessionMode(mode: SessionMode): String {
        return mode.name
    }
    
    @TypeConverter
    fun toSessionMode(value: String): SessionMode {
        return SessionMode.valueOf(value)
    }

    @TypeConverter
    fun fromSessionStatus(status: SessionStatus): String {
        return status.name
    }

    @TypeConverter
    fun toSessionStatus(value: String): SessionStatus {
        return SessionStatus.valueOf(value)
    }

    @TypeConverter
    fun fromFrameQuality(quality: FrameQuality): String {
        return quality.name
    }

    @TypeConverter
    fun toFrameQuality(value: String): FrameQuality {
        return FrameQuality.valueOf(value)
    }
}
