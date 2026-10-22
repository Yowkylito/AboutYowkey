package dev.yowkey.portfolio.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Warm paper and ink, one signal-orange accent. Every text pair below clears WCAG AA (4.5:1).
private val LightColors = lightColorScheme(
    primary = Color(0xFFC2410C),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFE4D5),
    onPrimaryContainer = Color(0xFF5A1D04),
    background = Color(0xFFF7F5F0),
    onBackground = Color(0xFF16181D),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF16181D),
    surfaceVariant = Color(0xFFEDEAE3),
    onSurfaceVariant = Color(0xFF545A66),
    outline = Color(0xFFCFCAC0),
    outlineVariant = Color(0xFFE4E0D7),
    error = Color(0xFFB3261E),
    // Menus and other containers default to Material's lavender unless the container roles are set.
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFBFAF6),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFF2EFE8),
    surfaceContainerHighest = Color(0xFFEDEAE3),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFB923C),
    onPrimary = Color(0xFF1A0E05),
    primaryContainer = Color(0xFF4A2410),
    onPrimaryContainer = Color(0xFFFFDCC8),
    background = Color(0xFF0E1014),
    onBackground = Color(0xFFECEDEF),
    surface = Color(0xFF161A20),
    onSurface = Color(0xFFECEDEF),
    surfaceVariant = Color(0xFF1E232B),
    onSurfaceVariant = Color(0xFF9AA1AD),
    outline = Color(0xFF3A414C),
    outlineVariant = Color(0xFF262B33),
    error = Color(0xFFF2B8B5),
    surfaceContainerLowest = Color(0xFF0B0D10),
    surfaceContainerLow = Color(0xFF12151A),
    surfaceContainer = Color(0xFF1A1E25),
    surfaceContainerHigh = Color(0xFF20252D),
    surfaceContainerHighest = Color(0xFF262B33),
)

private val BaseTypography = Typography()

private val PortfolioTypography = BaseTypography.copy(
    displayLarge = BaseTypography.displayLarge.copy(
        fontWeight = FontWeight.Bold, fontSize = 72.sp, lineHeight = 76.sp, letterSpacing = (-2).sp,
    ),
    displayMedium = BaseTypography.displayMedium.copy(
        fontWeight = FontWeight.Bold, fontSize = 48.sp, lineHeight = 52.sp, letterSpacing = (-1.2).sp,
    ),
    headlineMedium = BaseTypography.headlineMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
    headlineSmall = BaseTypography.headlineSmall.copy(fontWeight = FontWeight.Normal, lineHeight = 34.sp),
    titleLarge = BaseTypography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = BaseTypography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = BaseTypography.bodyLarge.copy(fontSize = 18.sp, lineHeight = 28.sp),
    bodyMedium = BaseTypography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 23.sp),
    labelLarge = BaseTypography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    labelMedium = BaseTypography.labelMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 1.4.sp),
)

/** True when the user asked the OS for less motion; reveal animations are skipped. */
val LocalReducedMotion = staticCompositionLocalOf { false }

@Composable
fun PortfolioTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = PortfolioTypography,
        content = content,
    )
}
