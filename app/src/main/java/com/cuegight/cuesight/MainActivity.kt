package com.cuegight.cuesight

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.cuegight.cuesight.data.model.SessionMode
import com.cuegight.cuesight.navigation.Screen
import com.cuegight.cuesight.ui.screens.AddStudentScreen
import com.cuegight.cuesight.ui.screens.EntryScreen
import com.cuegight.cuesight.ui.screens.SettingsScreen
import com.cuegight.cuesight.ui.screens.StudentDetailScreen
import com.cuegight.cuesight.ui.screens.StudentsScreen
import com.cuegight.cuesight.ui.screens.developer.DeveloperTestScreen
import com.cuegight.cuesight.ui.screens.practice.PracticeSessionScreen
import com.cuegight.cuesight.ui.screens.teaching.TeachingSessionScreen
import com.cuegight.cuesight.ui.theme.CueSightTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            CueSightTheme {
                CueSightApp()
            }
        }
    }
}

@Composable
fun CueSightApp() {
    val navController = rememberNavController()
    
    NavHost(
        navController = navController,
        startDestination = Screen.Entry.route
    ) {
        composable(Screen.Entry.route) {
            EntryScreen(
                onNavigateToStudentEntry = {
                    navController.navigate(Screen.Students.route)
                },
                onNavigateToDeveloperTest = {
                    navController.navigate(Screen.DeveloperTest.route)
                }
            )
        }
        
        composable(Screen.Students.route) {
            StudentsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToAddStudent = {
                    navController.navigate(Screen.AddStudent.route)
                },
                onNavigateToStudent = { studentId ->
                    navController.navigate(Screen.StudentDetail.createRoute(studentId))
                }
            )
        }
        
        composable(Screen.AddStudent.route) {
            AddStudentScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        composable(
            route = Screen.StudentDetail.route,
            arguments = listOf(navArgument("studentId") { type = NavType.LongType })
        ) { backStackEntry ->
            val studentId = backStackEntry.arguments?.getLong("studentId") ?: 0L
            StudentDetailScreen(
                studentId = studentId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToSession = { id, mode ->
                    val route = if (mode == SessionMode.TEACHING.name) {
                        Screen.TeachingSession.createRoute(id)
                    } else {
                        Screen.PracticeSession.createRoute(id)
                    }
                    navController.navigate(route)
                }
            )
        }
        
        composable(
            route = Screen.TeachingSession.route,
            arguments = listOf(navArgument("studentId") { type = NavType.LongType })
        ) { backStackEntry ->
            val studentId = backStackEntry.arguments?.getLong("studentId") ?: 0L
            TeachingSessionScreen(
                studentId = studentId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.PracticeSession.route,
            arguments = listOf(navArgument("studentId") { type = NavType.LongType })
        ) { backStackEntry ->
            val studentId = backStackEntry.arguments?.getLong("studentId") ?: 0L
            PracticeSessionScreen(
                studentId = studentId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.DeveloperTest.route) {
            DeveloperTestScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        
        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
