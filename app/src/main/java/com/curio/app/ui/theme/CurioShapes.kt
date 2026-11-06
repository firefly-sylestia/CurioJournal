package com.curio.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Curio's shape system — see Curio shape contract.
 *
 * M3 shape tokens pushed rounder across the board:
 * - Small components (chips, small buttons) ..... 16dp corner radius
 * - Medium components (cards) ................... 24dp corner radius
 * - Large components (sheets, dialogs) .......... 32dp corner radius top
 * - The Spin dial itself ......................... perfect circle
 *
 * Nothing in the app should have a hard 90° corner except dividers/rules.
 */
val CurioShapes: Shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),     // M3 default: 4dp — slightly bumped for chip roundness
    small      = RoundedCornerShape(16.dp),    // Curio: chips, small buttons
    medium     = RoundedCornerShape(24.dp),    // Curio: cards
    large      = RoundedCornerShape(32.dp),    // Curio: sheets, dialogs (top corners)
    extraLarge = RoundedCornerShape(48.dp)     // M3 default: 28dp — extra-curvy for special cases
)