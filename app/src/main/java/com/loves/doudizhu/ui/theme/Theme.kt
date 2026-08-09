package com.loves.doudizhu.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val AppColors = darkColorScheme(
    primary = Color(0xFFE97D58),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF9E5139),
    onPrimaryContainer = Color.White,
    secondary = Color(0xFFF2D675),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF685D2F),
    onSecondaryContainer = Color.White,
    tertiary = Color(0xFF78D6A1),
    onTertiary = Color.White,
    background = Color(0xFF091F27),
    onBackground = Color(0xFFF7FAF9),
    surface = Color(0xFF12333B),
    onSurface = Color(0xFFF7FAF9),
    surfaceVariant = Color(0xFF24464D),
    onSurfaceVariant = Color(0xFFB4CED0),
    outline = Color(0xFF6B898D)
)

private val AppShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
)

@Composable
fun DoudizhuTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColors,
        shapes = AppShapes,
        typography = Typography(),
        content = content
    )
}
