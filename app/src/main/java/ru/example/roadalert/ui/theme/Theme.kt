package ru.example.roadalert.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Тема водительского интерфейса: высокий контраст, крупные цифры.
 * Днём — светлая, ночью — тёмная; в поездке и HUD принудительно тёмная.
 */

internal val AlertRed = Color(0xFFE53935)
internal val AlertAmber = Color(0xFFFFB300)
internal val SafeGreen = Color(0xFF43A047)
internal val SpeedWhite = Color(0xFFF5F7FA)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7FB2FF),
    onPrimary = Color(0xFF00315C),
    secondary = Color(0xFFB9C6DC),
    background = Color(0xFF101418),
    onBackground = SpeedWhite,
    surface = Color(0xFF171C21),
    onSurface = SpeedWhite,
    surfaceVariant = Color(0xFF232A31),
    error = AlertRed,
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF1160A8),
    onPrimary = Color.White,
    secondary = Color(0xFF53607A),
    background = Color(0xFFF7F9FC),
    onBackground = Color(0xFF15181C),
    surface = Color.White,
    onSurface = Color(0xFF15181C),
    surfaceVariant = Color(0xFFE1E6EE),
    error = AlertRed,
)

private val AppTypography = Typography(
    displayLarge = TextStyle(fontSize = 96.sp, fontWeight = FontWeight.Bold),
    displayMedium = TextStyle(fontSize = 64.sp, fontWeight = FontWeight.Bold),
    displaySmall = TextStyle(fontSize = 44.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 17.sp),
    bodyMedium = TextStyle(fontSize = 15.sp),
    labelLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun RoadAlertTheme(
    forceDark: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = forceDark || isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}
