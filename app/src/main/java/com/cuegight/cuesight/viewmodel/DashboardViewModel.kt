package com.cuegight.cuesight.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cuegight.cuesight.data.model.Student
import com.cuegight.cuesight.data.repository.EmotionLogRepository
import com.cuegight.cuesight.data.repository.SessionRepository
import com.cuegight.cuesight.data.repository.StudentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DashboardState(
    val students: List<Student> = emptyList(),
    val totalStudents: Int = 0,
    val totalSessions: Int = 0,
    val recentSessions: Int = 0,
    val isLoading: Boolean = true
)

class DashboardViewModel(
    private val studentRepository: StudentRepository,
    private val sessionRepository: SessionRepository,
    private val emotionLogRepository: EmotionLogRepository
) : ViewModel() {
    
    private val _state = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state.asStateFlow()
    
    init {
        loadDashboardData()
    }
    
    private fun loadDashboardData() {
        viewModelScope.launch {
            studentRepository.getAllStudents().collect { students ->
                val totalStudents = students.size
                
                // Get all sessions
                sessionRepository.getAllSessions().collect { sessions ->
                    val totalSessions = sessions.size
                    val recentSessions = sessions.take(5).size
                    
                    _state.value = DashboardState(
                        students = students.take(5),
                        totalStudents = totalStudents,
                        totalSessions = totalSessions,
                        recentSessions = recentSessions,
                        isLoading = false
                    )
                }
            }
        }
    }
    
    fun refresh() {
        loadDashboardData()
    }
}
