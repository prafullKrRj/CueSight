package com.cuegight.cuesight.ui.screens.teaching

import androidx.compose.runtime.Composable
import com.cuegight.cuesight.data.model.SessionMode
import com.cuegight.cuesight.ui.screens.core.SessionCoreScreen

@Composable
fun TeachingSessionScreen(
    studentId: Long,
    onNavigateBack: () -> Unit
) {
    SessionCoreScreen(
        studentId = studentId,
        sessionMode = SessionMode.TEACHING,
        modeTitle = "Teaching Mode",
        modeDescription = "Demonstrate emotions for the student to observe and learn",
        onNavigateBack = onNavigateBack
    )
}
