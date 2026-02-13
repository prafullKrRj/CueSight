package com.cuegight.cuesight.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

enum class SessionMode {
    TEACHING, PRACTICE, TEST
}

enum class SessionStatus {
    ACTIVE, COMPLETED, INTERRUPTED
}

@Entity(
    tableName = "sessions",
    foreignKeys = [
        ForeignKey(
            entity = Student::class,
            parentColumns = ["id"],
            childColumns = ["studentId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class Session(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val studentId: Long,
    val mode: SessionMode,
    val status: SessionStatus = SessionStatus.ACTIVE,
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long? = null,
    val durationSeconds: Long = 0,
    val totalEmotionsDetected: Int = 0,
    val notes: String = ""
)
