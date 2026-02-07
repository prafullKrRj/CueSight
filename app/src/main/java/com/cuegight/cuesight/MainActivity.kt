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
import com.cuegight.cuesight.navigation.Screen
import com.cuegight.cuesight.ui.screens.*
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
        startDestination = Screen.Dashboard.route
    ) {
        composable(Screen.Dashboard.route) {
            DashboardScreen(
                onNavigateToStudents = {
                    navController.navigate(Screen.Students.route)
                },
                onNavigateToStudent = { studentId ->
                    navController.navigate(Screen.StudentDetail.createRoute(studentId))
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
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
                    navController.navigate(Screen.Session.createRoute(id, mode))
                }
            )
        }
        
        composable(
            route = Screen.Session.route,
            arguments = listOf(
                navArgument("studentId") { type = NavType.LongType },
                navArgument("mode") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val studentId = backStackEntry.arguments?.getLong("studentId") ?: 0L
            val mode = backStackEntry.arguments?.getString("mode") ?: "PRACTICE"
            SessionScreen(
                studentId = studentId,
                mode = mode,
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
