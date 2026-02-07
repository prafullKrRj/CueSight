package com.cuegight.cuesight.data.database

import androidx.room.*
import com.cuegight.cuesight.data.model.Student
import kotlinx.coroutines.flow.Flow

@Dao
interface StudentDao {
    @Query("SELECT * FROM students ORDER BY createdAt DESC")
    fun getAllStudents(): Flow<List<Student>>
    
    @Query("SELECT * FROM students WHERE id = :id")
    suspend fun getStudentById(id: Long): Student?
    
    @Query("SELECT * FROM students WHERE id = :id")
    fun getStudentByIdFlow(id: Long): Flow<Student?>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStudent(student: Student): Long
    
    @Update
    suspend fun updateStudent(student: Student)
    
    @Delete
    suspend fun deleteStudent(student: Student)
    
    @Query("SELECT COUNT(*) FROM students")
    suspend fun getStudentCount(): Int
}
