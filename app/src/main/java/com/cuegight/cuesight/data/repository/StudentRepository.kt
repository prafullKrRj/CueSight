package com.cuegight.cuesight.data.repository

import com.cuegight.cuesight.data.database.CueSightDatabase
import com.cuegight.cuesight.data.model.Student
import kotlinx.coroutines.flow.Flow

class StudentRepository(private val database: CueSightDatabase) {
    private val studentDao = database.studentDao()
    
    fun getAllStudents(): Flow<List<Student>> = studentDao.getAllStudents()
    
    suspend fun getStudentById(id: Long): Student? = studentDao.getStudentById(id)
    
    fun getStudentByIdFlow(id: Long): Flow<Student?> = studentDao.getStudentByIdFlow(id)
    
    suspend fun insertStudent(student: Student): Long = studentDao.insertStudent(student)
    
    suspend fun updateStudent(student: Student) = studentDao.updateStudent(student)
    
    suspend fun deleteStudent(student: Student) = studentDao.deleteStudent(student)
    
    suspend fun getStudentCount(): Int = studentDao.getStudentCount()
}
