package dev.yowkey.portfolio

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import dev.yowkey.portfolio.ui.PortfolioRoute
import dev.yowkey.portfolio.ui.image.LocalScreenshotImages
import dev.yowkey.portfolio.ui.image.ScreenshotImages
import dev.yowkey.portfolio.ui.theme.LocalReducedMotion
import dev.yowkey.portfolio.ui.theme.PortfolioTheme
import org.koin.compose.koinInject

/**
 * The shared app. Each platform's `main` starts Koin, then hosts this.
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
    PortfolioTheme {
        CompositionLocalProvider(
            LocalReducedMotion provides reducedMotion,
            LocalScreenshotImages provides koinInject<ScreenshotImages>(),
        ) {
            PortfolioRoute(platformLabel = platformLabel, onContentShown = onContentShown)
        }
    }
}
