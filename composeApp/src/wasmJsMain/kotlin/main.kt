@file:OptIn(ExperimentalWasmJsInterop::class)

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import dev.yowkey.portfolio.App
import dev.yowkey.portfolio.di.appModule
import org.koin.core.context.startKoin

private fun prefersReducedMotion(): Boolean =
    js("window.matchMedia('(prefers-reduced-motion: reduce)').matches")

/** Hands the page from the static HTML to the Compose canvas (see the `app-ready` rules in index.html). */
private fun markAppReady(): Unit = js(
    """(function () {
        document.body.classList.add('app-ready');
        var app = document.getElementById('app');
        if (app) app.removeAttribute('aria-hidden');
    })()""",
)

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    startKoin { modules(appModule) }
    ComposeViewport(viewportContainerId = "app") {
        App(
            platformLabel = "Kotlin/Wasm in your browser",
            reducedMotion = prefersReducedMotion(),
            onContentShown = ::markAppReady,
        )
    }
}
