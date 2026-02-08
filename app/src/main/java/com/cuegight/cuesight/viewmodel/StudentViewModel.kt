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

data class StudentListState(
    val students: List<Student> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

class StudentViewModel(
    private val studentRepository: StudentRepository,
    private val sessionRepository: SessionRepository,
    private val emotionLogRepository: EmotionLogRepository
) : ViewModel() {
    
    private val _state = MutableStateFlow(StudentListState())
    val state: StateFlow<StudentListState> = _state.asStateFlow()
    
    init {
        loadStudents()
    }
    
    private fun loadStudents() {
        viewModelScope.launch {
            try {
                studentRepository.getAllStudents().collect { students ->
                    _state.value = StudentListState(
                        students = students,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _state.value = StudentListState(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }
    
    fun addStudent(name: String, age: Int, diagnosis: String, notes: String) {
        viewModelScope.launch {
            val student = Student(
                name = name,
                age = age,
                diagnosis = diagnosis,
                notes = notes
            )
            studentRepository.insertStudent(student)
        }
    }
    
    fun updateStudent(student: Student) {
        viewModelScope.launch {
            studentRepository.updateStudent(student)
        }
    }
    
    fun deleteStudent(student: Student) {
        viewModelScope.launch {
            studentRepository.deleteStudent(student)
        }
    }
    
    fun getStudentById(id: Long) = studentRepository.getStudentByIdFlow(id)

    fun getSessionsByStudent(studentId: Long) = sessionRepository.getSessionsByStudent(studentId)

    fun getConfusionMatrix(studentId: Long) = emotionLogRepository.getConfusionMatrixData(studentId)

    fun getAccuracyTrend(studentId: Long) = emotionLogRepository.getAccuracyOverTime(studentId)

    fun getEmotionAccuracy(studentId: Long) = emotionLogRepository.getEmotionAccuracy(studentId)
}
