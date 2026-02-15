package com.cuegight.cuesight.navigation

sealed class Screen(val route: String) {
    // New flow screens
    object Splash : Screen("splash")
    object Connection : Screen("connection")
    
    // New HTTP-based session screens
    object NewTeachingSession : Screen("new_teaching_session/{studentId}/{studentName}") {
        fun createRoute(studentId: Long, studentName: String) = 
            "new_teaching_session/$studentId/${studentName.replace("/", "_")}"
    }
    object NewPracticeSession : Screen("new_practice_session/{studentId}/{studentName}") {
        fun createRoute(studentId: Long, studentName: String) = 
            "new_practice_session/$studentId/${studentName.replace("/", "_")}"
    }
    
    // Legacy screens (will be refactored)
    object Entry : Screen("entry")
    object Dashboard : Screen("dashboard")
    object Students : Screen("students")
    object AddStudent : Screen("add_student")
    object StudentDetail : Screen("student_detail/{studentId}") {
        fun createRoute(studentId: Long) = "student_detail/$studentId"
    }
    object TeachingSession : Screen("teaching_session/{studentId}") {
        fun createRoute(studentId: Long) = "teaching_session/$studentId"
    }
    object PracticeSession : Screen("practice_session/{studentId}") {
        fun createRoute(studentId: Long) = "practice_session/$studentId"
    }
    object DeveloperTest : Screen("developer_test")
    object SessionDetail : Screen("session_detail/{sessionId}") {
        fun createRoute(sessionId: Long) = "session_detail/$sessionId"
    }
    object Settings : Screen("settings")
    object Onboarding : Screen("onboarding")
}
