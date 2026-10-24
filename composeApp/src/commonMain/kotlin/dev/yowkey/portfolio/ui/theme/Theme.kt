package dev.yowkey.portfolio.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Neutral zinc ink on near-white, one blue accent. Every text pair below clears WCAG AA (4.5:1): the accent on
// the page is 5.0:1, the muted text 7.3:1. The static page's CSS in web/index.template.html carries the same values.
private val LightColors = lightColorScheme(
    primary = Color(0xFF2563EB),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDBEAFE),
    onPrimaryContainer = Color(0xFF1E3A8A),
    background = Color(0xFFFAFAFA),
    onBackground = Color(0xFF09090B),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF09090B),
    surfaceVariant = Color(0xFFE8ECF0),
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFFD4D4D8),
    outlineVariant = Color(0xFFE4E4E7),
    error = Color(0xFFB91C1C),
    // Menus and other containers default to Material's lavender unless the container roles are set.
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFCFCFC),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFF1F3F5),
    surfaceContainerHighest = Color(0xFFE8ECF0),
)

// The same scale inverted, for visitors who ask for it: the accent lightens so it keeps its contrast on the dark page.
private val DarkColors = darkColorScheme(
    primary = Color(0xFF60A5FA),
    onPrimary = Color(0xFF0B1D3F),
    primaryContainer = Color(0xFF1E3A8A),
    onPrimaryContainer = Color(0xFFBFDBFE),
    background = Color(0xFF0C0E13),
    onBackground = Color(0xFFF4F4F5),
    surface = Color(0xFF15181F),
    onSurface = Color(0xFFF4F4F5),
    surfaceVariant = Color(0xFF1E2229),
    onSurfaceVariant = Color(0xFFA1A1AA),
    outline = Color(0xFF3F3F46),
    outlineVariant = Color(0xFF27272A),
    error = Color(0xFFF2B8B5),
    surfaceContainerLowest = Color(0xFF070809),
    surfaceContainerLow = Color(0xFF101318),
    surfaceContainer = Color(0xFF181C23),
    surfaceContainerHigh = Color(0xFF1F242B),
    surfaceContainerHighest = Color(0xFF272B33),
)

/**
 * The edge of a form control that nothing else marks: an empty text field's border, an unlit star of the rating.
 * The schemes' outline is a hairline for cards and badges, 1.4:1 on the light page and 1.9:1 on the dark one, and a
 * control's edge needs 3:1 (WCAG 1.4.11). Zinc 500, the next step of the same scale, is 4.6:1 on the light page and
 * 4.0:1 on the dark one (3.7:1 on a dark card), so one value serves both themes.
 */
val ControlOutline = Color(0xFF71717A)

private val BaseTypography = Typography()

/**
 * One scale for both screens, so a heading means the same thing wherever it is: display for the name and a
 * project's title; headlineMedium for every section, the page's and the project page's; headlineSmall for a lead
 * sentence (the hero's headline, the Contact line, a project's tagline); titleLarge for cards and forms;
 * titleMedium for an item in a list (a job, an award, a decision). Body text is set at 1.6 times its size, the
 * line height long text reads easiest at; headings sit tighter, since they are one or two lines.
 */
private val PortfolioTypography = BaseTypography.copy(
    displayLarge = BaseTypography.displayLarge.copy(
        fontWeight = FontWeight.Bold, fontSize = 72.sp, lineHeight = 76.sp, letterSpacing = (-2).sp,
    ),
    displayMedium = BaseTypography.displayMedium.copy(
        fontWeight = FontWeight.Bold, fontSize = 48.sp, lineHeight = 52.sp, letterSpacing = (-1.2).sp,
    ),
    // A project's title on a phone: a step above the section headings under it, small enough for two words a line.
    displaySmall = BaseTypography.displaySmall.copy(
        fontWeight = FontWeight.Bold, fontSize = 36.sp, lineHeight = 42.sp, letterSpacing = (-0.8).sp,
    ),
    headlineMedium = BaseTypography.headlineMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
    headlineSmall = BaseTypography.headlineSmall.copy(fontWeight = FontWeight.Normal, lineHeight = 34.sp),
    titleLarge = BaseTypography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = BaseTypography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = BaseTypography.bodyLarge.copy(fontSize = 18.sp, lineHeight = 29.sp),
    bodyMedium = BaseTypography.bodyMedium.copy(fontSize = 15.sp, lineHeight = 24.sp),
    labelLarge = BaseTypography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    labelMedium = BaseTypography.labelMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 1.4.sp),
)

/** True when the user asked the OS for less motion; reveal animations are skipped. */
val LocalReducedMotion = staticCompositionLocalOf { false }

/**
 * Which theme the page is in and how to switch it. The page is light until the visitor asks for dark; [App]
 * owns the choice and keeps it for the next visit through [savedDarkTheme] and [saveDarkTheme]. [toggle] takes
 * where the switch is, in the page's coordinates: the new theme spreads from there (see [ThemeReveal]).
 * [pressed] runs as a pointer goes down on the switch and [pressCancelled] if it then leaves without a switch, so
 * the reveal can get ready during the press.
 */
@Immutable
class ThemeSwitch(
    val dark: Boolean,
    val toggle: (from: Offset) -> Unit,
    val pressed: () -> Unit = {},
    val pressCancelled: () -> Unit = {},
)

val LocalThemeSwitch = staticCompositionLocalOf { ThemeSwitch(dark = false, toggle = {}) }

/** The page's colours and type. Light by default: the OS's preference is not consulted, the visitor's switch is. */
@Composable
fun PortfolioTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = PortfolioTypography,
        content = content,
    )
}
