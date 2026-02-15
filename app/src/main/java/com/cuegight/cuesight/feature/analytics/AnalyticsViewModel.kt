package com.cuegight.cuesight.feature.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cuegight.cuesight.data.repository.SessionRepository
import com.cuegight.cuesight.data.repository.StudentRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * Analytics ViewModel
 * Provides student progress and session analytics
 */
class AnalyticsViewModel(
    private val studentRepository: StudentRepository,
    private val sessionRepository: SessionRepository
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
                // Combine student and session data
                studentRepository.getAllStudents()
                    .combine(sessionRepository.getAllSessions()) { students, sessions ->
                        // Calculate analytics for each student
                        students.map { student ->
                            val studentSessions = sessions.filter { it.studentId == student.id }
                            val totalSessions = studentSessions.size
                            val totalEmotions = studentSessions.sumOf { it.totalEmotionsDetected }
                            val totalDuration = studentSessions.sumOf { it.durationSeconds }
                            val averageAccuracy = if (studentSessions.isNotEmpty()) {
                                // Note: Accuracy calculation would need more detailed session data
                                0.0f // Placeholder
                            } else 0.0f

                            StudentAnalytics(
                                studentId = student.id,
                                studentName = student.name,
                                totalSessions = totalSessions,
                                totalEmotions = totalEmotions,
                                totalDurationSeconds = totalDuration,
                                averageAccuracy = averageAccuracy,
                                lastSessionDate = studentSessions.maxByOrNull { it.startTime }?.startTime
                            )
                        }
                    }
                    .catch { e ->
                        _state.value = _state.value.copy(
                            isLoading = false,
                            error = e.message
                        )
                    }
                    .collect { analytics ->
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
