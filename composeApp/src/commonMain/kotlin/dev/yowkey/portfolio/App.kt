package dev.yowkey.portfolio

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.yowkey.portfolio.ui.PortfolioRoute
import dev.yowkey.portfolio.ui.image.LocalScreenshotImages
import dev.yowkey.portfolio.ui.image.ScreenshotImages
import dev.yowkey.portfolio.ui.theme.LocalReducedMotion
import dev.yowkey.portfolio.ui.theme.LocalThemeSwitch
import dev.yowkey.portfolio.ui.theme.PortfolioTheme
import dev.yowkey.portfolio.ui.theme.ThemeSwitch
import dev.yowkey.portfolio.ui.theme.saveDarkTheme
import dev.yowkey.portfolio.ui.theme.savedDarkTheme
import org.koin.compose.koinInject

/**
 * The shared app. Each platform's `main` starts Koin, then hosts this. The theme is light unless the visitor
 * switched to dark, here or on an earlier visit; the choice is kept per platform (see `ThemeStore`).
 *
 * @param platformLabel what is rendering the page, shown in the hero and footer ("Kotlin/Wasm in your browser").
 * @param onContentShown called once real content has been drawn; the web host swaps out its static HTML then.
 */
@Composable
fun App(
    platformLabel: String,
    reducedMotion: Boolean = false,
    onContentShown: () -> Unit = {},
) {
    var dark by remember { mutableStateOf(savedDarkTheme() ?: false) }
    val theme = remember(dark) {
        ThemeSwitch(dark) {
            val next = !dark
            saveDarkTheme(next)
            dark = next
        }
    }
    PortfolioTheme(darkTheme = dark) {
        CompositionLocalProvider(
            LocalThemeSwitch provides theme,
            LocalReducedMotion provides reducedMotion,
            LocalScreenshotImages provides koinInject<ScreenshotImages>(),
        ) {
            PortfolioRoute(platformLabel = platformLabel, onContentShown = onContentShown)
        }
    }
}
