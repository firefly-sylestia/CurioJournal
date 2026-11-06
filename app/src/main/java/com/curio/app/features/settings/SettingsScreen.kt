package com.curio.app.features.settings

import androidx.compose.runtime.Composable
import androidx.navigation.NavController

/**
 * Compatibility entry point for older callers of the former monolithic
 * settings screen. The redesigned hub is now the single settings surface.
 */
@Composable
fun SettingsScreen(navController: NavController) {
    SettingsHubScreen(navController = navController)
}
