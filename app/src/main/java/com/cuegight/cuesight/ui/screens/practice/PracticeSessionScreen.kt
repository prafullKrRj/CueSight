package com.cuegight.cuesight.ui.screens.practice

import androidx.compose.runtime.Composable
import com.cuegight.cuesight.data.model.SessionMode
import com.cuegight.cuesight.ui.screens.core.SessionCoreScreen

@Composable
fun PracticeSessionScreen(
    studentId: Long,
    onNavigateBack: () -> Unit
) {
    SessionCoreScreen(
        studentId = studentId,
        sessionMode = SessionMode.PRACTICE,
        modeTitle = "Practice Mode",
        modeDescription = "Teacher demonstrates emotions while OLED shows '?' until feedback",
        onNavigateBack = onNavigateBack
    )
}
