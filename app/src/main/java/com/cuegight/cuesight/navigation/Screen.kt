package com.cuegight.cuesight.navigation

sealed class Screen(val route: String) {
    object Dashboard : Screen("dashboard")
    object Students : Screen("students")
    object AddStudent : Screen("add_student")
    object StudentDetail : Screen("student_detail/{studentId}") {
        fun createRoute(studentId: Long) = "student_detail/$studentId"
    }
    object Session : Screen("session/{studentId}/{mode}") {
        fun createRoute(studentId: Long, mode: String) = "session/$studentId/$mode"
    }
    object SessionDetail : Screen("session_detail/{sessionId}") {
        fun createRoute(sessionId: Long) = "session_detail/$sessionId"
    }
    object Settings : Screen("settings")
    object Onboarding : Screen("onboarding")
}
