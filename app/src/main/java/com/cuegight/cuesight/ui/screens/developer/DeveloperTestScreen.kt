package com.cuegight.cuesight.ui.screens.developer

import androidx.compose.runtime.Composable
import com.cuegight.cuesight.data.model.SessionMode
import com.cuegight.cuesight.ui.screens.core.SessionCoreScreen

@Composable
fun DeveloperTestScreen(
    onNavigateBack: () -> Unit
) {
    SessionCoreScreen(
        studentId = 0L,
        sessionMode = SessionMode.TEST,
        modeTitle = "Developer Test Mode",
        modeDescription = "Temporary developer testing with full stream and live emotion detection",
        onNavigateBack = onNavigateBack
    )
}
