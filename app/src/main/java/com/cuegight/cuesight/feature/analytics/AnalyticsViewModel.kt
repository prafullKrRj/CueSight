package com.cuegight.cuesight.feature.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cuegight.cuesight.data.repository.StudentRepository
import com.cuegight.cuesight.feature.practice.data.repository.PracticeRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * Analytics ViewModel
 * Provides student progress and session analytics using PracticeRepository
 */
class AnalyticsViewModel(
    private val studentRepository: StudentRepository,
    private val practiceRepository: PracticeRepository
) : ViewModel() {

    private val _state = MutableStateFlow(AnalyticsState())
    val state: StateFlow<AnalyticsState> = _state.asStateFlow()

    init {
        loadAnalytics()
    }

    private fun loadAnalytics() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)

            try {
                studentRepository.getAllStudents()
                    .collect { students ->
                        // Get all sessions once per load
                        val allSessions = practiceRepository.getAllSessions()
                        
                        // Calculate analytics for each student
                        val analytics = students.map { student ->
                            val studentSessions = allSessions.filter { it.studentId == student.id }
                            // Only count completed sessions for analytics
                            val completedSessions = studentSessions.filter { it.endTime != null }
                            val totalSessions = completedSessions.size
                            val totalGuesses = completedSessions.sumOf { it.totalGuesses }
                            val totalCorrect = completedSessions.sumOf { it.correctGuesses }
                            val totalDuration = completedSessions.sumOf { session ->
                                val endTime = session.endTime ?: 0L
                                if (endTime > 0) (endTime - session.startTime) / 1000 else 0L
                            }
                            val averageAccuracy = if (totalGuesses > 0) {
                                totalCorrect.toFloat() / totalGuesses.toFloat()
                            } else 0f

                            StudentAnalytics(
                                studentId = student.id,
                                studentName = student.name,
                                totalSessions = totalSessions,
                                totalEmotions = totalGuesses,
                                totalDurationSeconds = totalDuration,
                                averageAccuracy = averageAccuracy,
                                lastSessionDate = completedSessions.maxByOrNull { it.startTime }?.startTime
                            )
                        }
                        
                        _state.value = _state.value.copy(
                            isLoading = false,
                            studentAnalytics = analytics,
                            error = null
                        )
                    }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }

    fun refresh() {
        loadAnalytics()
    }
}

data class AnalyticsState(
    val isLoading: Boolean = false,
    val studentAnalytics: List<StudentAnalytics> = emptyList(),
    val error: String? = null
)

data class StudentAnalytics(
    val studentId: Long,
    val studentName: String,
    val totalSessions: Int,
    val totalEmotions: Int,
    val totalDurationSeconds: Long,
    val averageAccuracy: Float,
    val lastSessionDate: Long?
)
