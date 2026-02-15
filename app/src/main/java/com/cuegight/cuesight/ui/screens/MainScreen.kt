package com.cuegight.cuesight.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import com.cuegight.cuesight.feature.analytics.AnalyticsScreen
import com.cuegight.cuesight.ui.components.BottomNavBar
import com.cuegight.cuesight.ui.components.BottomNavTab

/**
 * Main screen wrapper with bottom navigation
 * Provides tabbed interface for Students, Analytics, and Settings
 */
@Composable
fun MainScreen(
    navController: NavHostController,
    initialTab: BottomNavTab = BottomNavTab.STUDENTS
) {
    var selectedTab by remember { mutableStateOf(initialTab) }

    Scaffold(
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
                        navController.navigate("student_detail/$studentId")
                    },
                    showBackButton = false,
                    modifier = Modifier.padding(paddingValues)
                )
            }
            BottomNavTab.SETTINGS -> {
                SettingsScreen(
                    onNavigateBack = { }, // No back button when in bottom nav
                    showBackButton = false
                )
            }
        }
    }
}
