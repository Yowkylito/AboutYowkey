package dev.yowkey.portfolio.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Warm paper and ink, one signal-orange accent. Every text pair below clears WCAG AA (4.5:1); ratios:
// light  ink 16.3 on background / 17.8 on surface, muted 6.4 / 6.9 (6.0 on chips), accent 4.75 / 5.2,
//        white on accent 5.2, onPrimaryContainer on primaryContainer 10.7.
// dark   ink 16.3 / 14.9, muted 7.3 / 6.7 (5.9 on chips), accent 8.4 / 7.7, onPrimary on accent 8.4,
//        onPrimaryContainer on primaryContainer 10.5.
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

/**
 * Five contrast steps a skimming reader can tell apart: display (name) > headline (role, section titles) >
 * title (cards, jobs, wins) > body > label. Display sizes track tight (about -3%); body runs at a ~1.55 line
 * height. Bundled Roboto only: hierarchy comes from size, weight and colour, so no font adds to the payload.
 */
private val PortfolioTypography = BaseTypography.copy(
    displayLarge = BaseTypography.displayLarge.copy(
        fontWeight = FontWeight.Bold, fontSize = 72.sp, lineHeight = 76.sp, letterSpacing = (-2.4).sp,
    ),
    displayMedium = BaseTypography.displayMedium.copy(
        fontWeight = FontWeight.Bold, fontSize = 46.sp, lineHeight = 50.sp, letterSpacing = (-1.4).sp,
    ),
    headlineMedium = BaseTypography.headlineMedium.copy(
        fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-0.6).sp,
    ),
    headlineSmall = BaseTypography.headlineSmall.copy(
        fontWeight = FontWeight.Medium, fontSize = 24.sp, lineHeight = 33.sp, letterSpacing = (-0.2).sp,
    ),
    titleLarge = BaseTypography.titleLarge.copy(
        fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.2).sp,
    ),
    titleMedium = BaseTypography.titleMedium.copy(
        fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 24.sp, letterSpacing = 0.sp,
    ),
    bodyLarge = BaseTypography.bodyLarge.copy(fontSize = 18.sp, lineHeight = 28.sp, letterSpacing = 0.1.sp),
    bodyMedium = BaseTypography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 23.sp, letterSpacing = 0.15.sp),
    bodySmall = BaseTypography.bodySmall.copy(fontSize = 13.sp, lineHeight = 19.sp, letterSpacing = 0.2.sp),
    labelLarge = BaseTypography.labelLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.sp),
    labelMedium = BaseTypography.labelMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 1.4.sp),
    labelSmall = BaseTypography.labelSmall.copy(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.2.sp),
)

/** Line-length caps (about 68 characters at the style they hold), so wide windows never set a 120-character line. */
object Measure {
    /** bodyLarge, 18sp. */
    val Lead = 620.dp

    /** bodyMedium, 15sp. */
    val Body = 560.dp
}

/**
 * How key facts in running text are marked: [strong] (accent, bold) for headline wins such as 53%, 2.4x or #1,
 * [figure] (ink, semibold) for plain counts such as 450 composables. One accent, used only here and for the
 * primary action, availability and the active nav link.
 */
@Immutable
data class EmphasisStyles(val strong: SpanStyle, val figure: SpanStyle)

val LocalEmphasis = staticCompositionLocalOf {
    EmphasisStyles(SpanStyle(fontWeight = FontWeight.Bold), SpanStyle(fontWeight = FontWeight.SemiBold))
}

@Composable
fun PortfolioTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val emphasis = remember(colors) {
        EmphasisStyles(
            strong = SpanStyle(color = colors.primary, fontWeight = FontWeight.Bold),
            figure = SpanStyle(color = colors.onSurface, fontWeight = FontWeight.SemiBold),
        )
    }
    MaterialTheme(colorScheme = colors, typography = PortfolioTypography) {
        CompositionLocalProvider(LocalEmphasis provides emphasis, content = content)
    }
}
