package com.cuegight.cuesight.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.cuegight.cuesight.viewmodel.StudentViewModel
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddStudentScreen(
    onNavigateBack: () -> Unit,
    viewModel: StudentViewModel = koinViewModel()
) {
    var name by remember { mutableStateOf("") }
    var age by remember { mutableStateOf("") }
    var diagnosis by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var nameError by remember { mutableStateOf<String?>(null) }
    var ageError by remember { mutableStateOf<String?>(null) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add Student") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    nameError = null
                },
                label = { Text("Name *") },
                isError = nameError != null,
                supportingText = nameError?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            
            OutlinedTextField(
                value = age,
                onValueChange = {
                    if (it.isEmpty() || it.all { char -> char.isDigit() }) {
                        age = it
                        ageError = null
                    }
                },
                label = { Text("Age *") },
                isError = ageError != null,
                supportingText = ageError?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            
            OutlinedTextField(
                value = diagnosis,
                onValueChange = { diagnosis = it },
                label = { Text("Diagnosis (Optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("Notes (Optional)") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                maxLines = 5
            )
            
            Spacer(modifier = Modifier.weight(1f))
            
            Button(
                onClick = {
                    var hasError = false
                    
                    if (name.isBlank()) {
                        nameError = "Name is required"
                        hasError = true
                    }
                    
                    if (age.isBlank()) {
                        ageError = "Age is required"
                        hasError = true
                    } else if (age.toIntOrNull() == null || age.toInt() < 1 || age.toInt() > 150) {
                        ageError = "Please enter a valid age"
                        hasError = true
                    }
                    
                    if (!hasError) {
                        viewModel.addStudent(
                            name = name.trim(),
                            age = age.toInt(),
                            diagnosis = diagnosis.trim(),
                            notes = notes.trim()
                        )
                        onNavigateBack()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Add Student")
            }
        }
    }
}
