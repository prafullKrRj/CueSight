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
import com.cuegight.cuesight.feature.analytics.AnalyticsScreen
import com.cuegight.cuesight.feature.connection.ConnectionScreen
import com.cuegight.cuesight.feature.practice.NewPracticeScreen
import com.cuegight.cuesight.feature.splash.SplashScreen
import com.cuegight.cuesight.feature.teaching.NewTeachingScreen
import com.cuegight.cuesight.navigation.Screen
import com.cuegight.cuesight.ui.components.BottomNavTab
import com.cuegight.cuesight.ui.screens.AddStudentScreen
import com.cuegight.cuesight.ui.screens.MainScreen
import com.cuegight.cuesight.ui.screens.SettingsScreen
import com.cuegight.cuesight.ui.screens.StudentDetailScreen
import com.cuegight.cuesight.ui.screens.StudentsScreen
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
        startDestination = Screen.Splash.route
    ) {
        // ═══════════════════════════════════════════════════════
        // SPLASH & CONNECTION FLOW
        // ═══════════════════════════════════════════════════════

        composable(Screen.Splash.route) {
            SplashScreen(
                onNavigateToConnection = {
                    navController.navigate(Screen.Connection.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                }
            )
        }
        
        composable(Screen.Connection.route) {
            ConnectionScreen(
                onNavigateToHome = {
                    navController.navigate("main") {
                        popUpTo(Screen.Connection.route) { inclusive = true }
                    }
                }
            )
        }
        
        // ═══════════════════════════════════════════════════════
        // MAIN APP (with Bottom Navigation)
        // ═══════════════════════════════════════════════════════

        composable("main") {
            MainScreen(
                navController = navController,
                initialTab = BottomNavTab.STUDENTS
            )
        }
        
        // ═══════════════════════════════════════════════════════
        // STUDENT MANAGEMENT
        // ═══════════════════════════════════════════════════════

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
                onNavigateToSession = { id, name, mode ->
                    // Navigate to NEW HTTP-based screens
                    val route = when (mode) {
                        "TEACHING" -> Screen.NewTeachingSession.createRoute(id, name)
                        "PRACTICE" -> Screen.NewPracticeSession.createRoute(id, name)
                        else -> return@StudentDetailScreen
                    }
                    navController.navigate(route)
                }
            )
        }
        
        // ═══════════════════════════════════════════════════════
        // NEW HTTP-BASED SESSION SCREENS (ONLY THESE!)
        // ═══════════════════════════════════════════════════════

        composable(
            route = Screen.NewTeachingSession.route,
            arguments = listOf(
                navArgument("studentId") { type = NavType.LongType },
                navArgument("studentName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val studentId = backStackEntry.arguments?.getLong("studentId") ?: 0L
            val studentName = backStackEntry.arguments?.getString("studentName") ?: ""

            NewTeachingScreen(
                studentId = studentId,
                studentName = studentName,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = Screen.NewPracticeSession.route,
            arguments = listOf(
                navArgument("studentId") { type = NavType.LongType },
                navArgument("studentName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val studentId = backStackEntry.arguments?.getLong("studentId") ?: 0L
            val studentName = backStackEntry.arguments?.getString("studentName") ?: ""

            NewPracticeScreen(
                studentId = studentId,
                studentName = studentName,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        
        // ═══════════════════════════════════════════════════════
        // ANALYTICS & SETTINGS
        // ═══════════════════════════════════════════════════════

        composable(Screen.Analytics.route) {
            AnalyticsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToStudentAnalytics = { studentId ->
                    navController.navigate(Screen.StudentAnalyticsDashboard.createRoute(studentId))
                }
            )
        }
        
        composable(
            route = Screen.StudentAnalyticsDashboard.route,
            arguments = listOf(navArgument("studentId") { type = NavType.LongType })
        ) { backStackEntry ->
            val studentId = backStackEntry.arguments?.getLong("studentId") ?: 0L
            com.cuegight.cuesight.feature.analytics.StudentAnalyticsDashboardScreen(
                studentId = studentId,
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
