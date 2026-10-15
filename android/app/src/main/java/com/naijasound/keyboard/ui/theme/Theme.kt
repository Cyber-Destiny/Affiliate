package com.naijasound.keyboard.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

val NaijaGreen = Color(0xFF0A8F4D)
private val NaijaGreenBright = Color(0xFF2FD982)

private val LightColors = lightColorScheme(
    primary = NaijaGreen,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC9F4DA),
    onPrimaryContainer = Color(0xFF00210E),
    secondary = Color(0xFFFFB400),
    onSecondary = Color(0xFF261A00),
    secondaryContainer = Color(0xFFFFE7A8),
    onSecondaryContainer = Color(0xFF261A00),
    background = Color(0xFFF6F8F6),
    onBackground = Color(0xFF151A16),
    surface = Color.White,
    onSurface = Color(0xFF151A16),
    surfaceVariant = Color(0xFFE6EDE8),
    onSurfaceVariant = Color(0xFF44504A),
    surfaceContainer = Color(0xFFEDF2EE),
    surfaceContainerHigh = Color(0xFFE6EDE8),
    outline = Color(0xFFC3CCC6),
    outlineVariant = Color(0xFFDDE4DF),
    error = Color(0xFFD93025),
)

private val DarkColors = darkColorScheme(
    primary = NaijaGreenBright,
    onPrimary = Color(0xFF00391B),
    primaryContainer = Color(0xFF00522A),
    onPrimaryContainer = Color(0xFFB4F5CC),
    secondary = Color(0xFFFFC94A),
    onSecondary = Color(0xFF3F2E00),
    secondaryContainer = Color(0xFF5B4300),
    onSecondaryContainer = Color(0xFFFFE7A8),
    background = Color(0xFF0F1411),
    onBackground = Color(0xFFE6EBE7),
    surface = Color(0xFF161C18),
    onSurface = Color(0xFFE6EBE7),
    surfaceVariant = Color(0xFF26302A),
    onSurfaceVariant = Color(0xFFBCC8C0),
    surfaceContainer = Color(0xFF1B221E),
    surfaceContainerHigh = Color(0xFF232B26),
    outline = Color(0xFF55615A),
    outlineVariant = Color(0xFF333D37),
    error = Color(0xFFFF8A80),
)

private val base = Typography()

private val AppTypography = Typography(
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Black),
    headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.Black),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Black),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.Bold),
    titleSmall = base.titleSmall.copy(fontWeight = FontWeight.Bold),
    bodyLarge = base.bodyLarge,
    bodyMedium = base.bodyMedium,
    bodySmall = base.bodySmall,
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.Bold),
    labelMedium = base.labelMedium.copy(fontWeight = FontWeight.Bold),
    labelSmall = base.labelSmall,
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun NaijaSoundTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}

/** Each mood gets its own bold colour so tiles are easy to scan. */
object CategoryColors {
    private val known = mapOf(
        "laughing" to Color(0xFFFFC233),
        "vex" to Color(0xFFFF5A4E),
        "shock" to Color(0xFF9B5DE5),
        "leave me alone" to Color(0xFF3A86FF),
        "praise" to Color(0xFF00B37E),
        "broke" to Color(0xFF8D6E63),
        "love" to Color(0xFFFF4F9A),
        "sarcasm" to Color(0xFF00A9C7),
    )
    private val others = listOf(Color(0xFFFF8C42), Color(0xFF4D9DE0), Color(0xFF7BC950), Color(0xFFE15A97))

    fun background(category: String): Color =
        known[category.trim().lowercase()] ?: others[Math.floorMod(category.hashCode(), others.size)]

    fun content(background: Color): Color =
        if (background.luminance() > 0.5f) Color(0xFF1A1A1A) else Color.White
}
