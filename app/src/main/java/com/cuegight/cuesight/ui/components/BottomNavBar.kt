package com.cuegight.cuesight.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.cuegight.cuesight.ui.theme.SkyBluePalette

/**
 * Bottom navigation bar for main app sections
 * Provides easy access to Students, Analytics, and Settings
 */
@Composable
fun BottomNavBar(
    selectedTab: BottomNavTab,
    onTabSelected: (BottomNavTab) -> Unit
) {
    NavigationBar(
        containerColor = SkyBluePalette.Sky50,
        tonalElevation = 0.dp
    ) {
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
                onClick = { onTabSelected(tab) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = SkyBluePalette.Sky900,
                    selectedTextColor = SkyBluePalette.Sky900,
                    indicatorColor = SkyBluePalette.Sky200,
                    unselectedIconColor = SkyBluePalette.Sky700.copy(alpha = 0.75f),
                    unselectedTextColor = SkyBluePalette.Sky700.copy(alpha = 0.75f)
                )
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
