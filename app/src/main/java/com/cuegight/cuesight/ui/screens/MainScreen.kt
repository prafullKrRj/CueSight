package com.cuegight.cuesight.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavHostController
import com.cuegight.cuesight.feature.analytics.AnalyticsScreen
import com.cuegight.cuesight.ui.components.BottomNavBar
import com.cuegight.cuesight.ui.components.BottomNavTab
import com.cuegight.cuesight.ui.theme.SkyBluePalette

/**
 * Main screen wrapper with bottom navigation
 * Provides tabbed interface for Students, Analytics, and Settings
 */
@Composable
fun MainScreen(
    navController: NavHostController,
    initialTab: BottomNavTab = BottomNavTab.STUDENTS
) {
    var selectedTab by rememberSaveable { mutableStateOf(initialTab) }

    Scaffold(
        containerColor = SkyBluePalette.Sky50,
        contentColor = Color.Unspecified,
        bottomBar = {
            BottomNavBar(
                selectedTab = selectedTab,
                onTabSelected = { tab ->
                    selectedTab = tab
                }
            )
        }
    ) { paddingValues ->
        when (selectedTab) {
            BottomNavTab.STUDENTS -> {
                StudentsScreen(
                    onNavigateBack = { }, // No back button when in bottom nav
                    onNavigateToAddStudent = {
                        navController.navigate("add_student")
                    },
                    onNavigateToStudent = { studentId ->
                        navController.navigate("student_detail/$studentId")
                    },
                    showBackButton = false,
                    modifier = Modifier.padding(paddingValues)
                )
            }
            BottomNavTab.ANALYTICS -> {
                AnalyticsScreen(
                    onNavigateBack = { }, // No back button when in bottom nav
                    onNavigateToStudentAnalytics = { studentId ->
                        navController.navigate("student_analytics/$studentId")
                    },
                    showBackButton = false,
                    modifier = Modifier.padding(paddingValues)
                )
            }
            BottomNavTab.SETTINGS -> {
                SettingsScreen(
                    onNavigateBack = { }, // No back button when in bottom nav
                    showBackButton = false,
                    modifier = Modifier.padding(paddingValues)
                )
            }
        }
    }
}
