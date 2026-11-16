package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = GuardianTealDark,
    onPrimary = Color(0xFF003734),
    primaryContainer = GuardianTealContainerDark,
    onPrimaryContainer = Color(0xFF9DF2EC),
    secondary = Color(0xFF94A3B8),
    onSecondary = Color(0xFF0F172A),
    secondaryContainer = ObsidianSurfaceVariant,
    onSecondaryContainer = PearlText,
    tertiary = AlertAmber,
    onTertiary = Color.Black,
    tertiaryContainer = AlertAmberContainerDark,
    onTertiaryContainer = AlertAmberContainer,
    error = SosCrimsonLight,
    onError = Color.White,
    errorContainer = SosCrimsonContainerDark,
    onErrorContainer = SosCrimsonContainer,
    background = ObsidianBackground,
    onBackground = PearlText,
    surface = ObsidianSurface,
    onSurface = PearlText,
    surfaceVariant = ObsidianSurfaceVariant,
    onSurfaceVariant = PearlMuted,
    outline = HairlineDark,
)

private val LightColorScheme = lightColorScheme(
    primary = GuardianTeal,
    onPrimary = Color.White,
    primaryContainer = GuardianTealContainer,
    onPrimaryContainer = Color(0xFF00201E),
    secondary = InkSlateMuted,
    onSecondary = Color.White,
    secondaryContainer = IvorySurfaceVariant,
    onSecondaryContainer = InkNavy,
    tertiary = AlertAmber,
    onTertiary = Color.White,
    tertiaryContainer = AlertAmberContainer,
    onTertiaryContainer = Color(0xFF451A03),
    error = SosCrimson,
    onError = Color.White,
    errorContainer = SosCrimsonContainer,
    onErrorContainer = Color(0xFF410002),
    background = IvoryBackground,
    onBackground = InkNavy,
    surface = IvorySurface,
    onSurface = InkNavy,
    surfaceVariant = IvorySurfaceVariant,
    onSurfaceVariant = InkSlateMuted,
    outline = HairlineLight,
)

val SafeGuardShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun SafeGuardTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = SafeGuardTypography,
        shapes = SafeGuardShapes,
        content = content,
    )
}
