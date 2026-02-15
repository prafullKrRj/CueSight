package com.cuegight.cuesight.navigation

sealed class Screen(val route: String) {
    // ═══════════════════════════════════════════════════════
    // APP FLOW
    // ═══════════════════════════════════════════════════════
    object Splash : Screen("splash")
    object Connection : Screen("connection")
    
    // ═══════════════════════════════════════════════════════
    // STUDENT MANAGEMENT
    // ═══════════════════════════════════════════════════════
    object Students : Screen("students")
    object AddStudent : Screen("add_student")
    object StudentDetail : Screen("student_detail/{studentId}") {
        fun createRoute(studentId: Long) = "student_detail/$studentId"
    }

    // ═══════════════════════════════════════════════════════
    // NEW HTTP-BASED SESSION SCREENS
    // ═══════════════════════════════════════════════════════
    object NewTeachingSession : Screen("new_teaching_session/{studentId}/{studentName}") {
        fun createRoute(studentId: Long, studentName: String) = 
            "new_teaching_session/$studentId/${studentName.replace("/", "_")}"
    }

    object NewPracticeSession : Screen("new_practice_session/{studentId}/{studentName}") {
        fun createRoute(studentId: Long, studentName: String) = 
            "new_practice_session/$studentId/${studentName.replace("/", "_")}"
    }
    
    // ═══════════════════════════════════════════════════════
    // ANALYTICS & SETTINGS
    // ═══════════════════════════════════════════════════════
    object Analytics : Screen("analytics")
    object Settings : Screen("settings")
}
