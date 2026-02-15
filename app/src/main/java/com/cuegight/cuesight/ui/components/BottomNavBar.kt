package com.cuegight.cuesight.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Bottom navigation bar for main app sections
 * Provides easy access to Students, Analytics, and Settings
 */
@Composable
fun BottomNavBar(
    selectedTab: BottomNavTab,
    onTabSelected: (BottomNavTab) -> Unit
) {
    NavigationBar {
        BottomNavTab.values().forEach { tab ->
            NavigationBarItem(
                icon = {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.label
                    )
                },
                label = { Text(tab.label) },
                selected = selectedTab == tab,
                onClick = { onTabSelected(tab) }
            )
        }
    }
}

enum class BottomNavTab(
    val label: String,
    val icon: ImageVector,
    val route: String
) {
    STUDENTS(
        label = "Students",
        icon = Icons.Default.People,
        route = "students"
    ),
    ANALYTICS(
        label = "Analytics",
        icon = Icons.Default.BarChart,
        route = "analytics"
    ),
    SETTINGS(
        label = "Settings",
        icon = Icons.Default.Settings,
        route = "settings"
    )
}
