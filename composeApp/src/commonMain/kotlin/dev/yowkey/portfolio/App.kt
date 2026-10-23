package dev.yowkey.portfolio

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.yowkey.portfolio.ui.PortfolioRoute
import dev.yowkey.portfolio.ui.image.LocalScreenshotImages
import dev.yowkey.portfolio.ui.image.ScreenshotImages
import dev.yowkey.portfolio.ui.theme.LocalReducedMotion
import dev.yowkey.portfolio.ui.theme.LocalThemeSwitch
import dev.yowkey.portfolio.ui.theme.PortfolioTheme
import dev.yowkey.portfolio.ui.theme.ThemeSwitch
import dev.yowkey.portfolio.ui.theme.rememberThemeReveal
import dev.yowkey.portfolio.ui.theme.saveDarkTheme
import dev.yowkey.portfolio.ui.theme.savedDarkTheme
import dev.yowkey.portfolio.ui.theme.themeReveal
import org.koin.compose.koinInject

/**
 * The shared app. Each platform's `main` starts Koin, then hosts this. The theme is light unless the visitor
 * switched to dark, here or on an earlier visit; the choice is kept per platform (see `ThemeStore`). A switch
 * spreads the new theme over the page from the switch (see `ThemeReveal`).
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
    val reveal = rememberThemeReveal()
    val theme = remember(dark, reveal, reducedMotion) {
        ThemeSwitch(
            dark = dark,
            toggle = { from ->
                reveal.start(from, reducedMotion) {
                    dark = !dark
                    saveDarkTheme(dark)
                }
            },
            pressed = reveal::prepare,
            pressCancelled = reveal::drop,
        )
    }
    PortfolioTheme(darkTheme = dark) {
        // At the window's origin, so the switch's place in the root is its place here too.
        Box(
            Modifier.fillMaxSize().themeReveal(reveal, edge = MaterialTheme.colorScheme.primary),
            propagateMinConstraints = true,
        ) {
            CompositionLocalProvider(
                LocalThemeSwitch provides theme,
                LocalReducedMotion provides reducedMotion,
                LocalScreenshotImages provides koinInject<ScreenshotImages>(),
            ) {
                PortfolioRoute(platformLabel = platformLabel, onContentShown = onContentShown)
            }
        }
    }
}
