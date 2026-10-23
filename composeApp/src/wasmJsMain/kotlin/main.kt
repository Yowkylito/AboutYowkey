@file:OptIn(ExperimentalWasmJsInterop::class)

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import dev.yowkey.portfolio.App
import dev.yowkey.portfolio.di.appModule
import dev.yowkey.portfolio.di.platformModule
import org.koin.core.context.startKoin

private fun prefersReducedMotion(): Boolean =
    js("window.matchMedia('(prefers-reduced-motion: reduce)').matches")

/**
 * Hands the page from the static HTML to the Compose canvas (see the `app-ready` rules in index.html). The static
 * page stays rendered under the canvas so its paint still counts as the page's LCP, but leaves the focus order and
 * the accessibility tree, which now belong to the app.
 */
private fun markAppReady(): Unit = js(
    """(function () {
        var page = document.getElementById('static-scroll');
        if (page) { page.setAttribute('aria-hidden', 'true'); page.inert = true; }
        document.body.classList.add('app-ready');
        var app = document.getElementById('app');
        if (app) app.removeAttribute('aria-hidden');
        // Keys reach Compose only through its focused canvas. Nothing else is focusable once the static page is
        // inert, so take the focus if nobody holds it: a deep-linked project then closes on Escape straight away.
        if (app && (!document.activeElement || document.activeElement === document.body)) {
            var host = Array.prototype.find.call(app.querySelectorAll('*'), function (e) { return e.shadowRoot; });
            var canvas = host ? host.shadowRoot.querySelector('canvas') : app.querySelector('canvas');
            if (canvas) canvas.focus({ preventScroll: true });
        }
    })()""",
)

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    startKoin { modules(appModule, platformModule) }
    ComposeViewport(viewportContainerId = "app") {
        App(
            platformLabel = "Kotlin/Wasm in your browser",
            reducedMotion = prefersReducedMotion(),
            onContentShown = ::markAppReady,
        )
    }
}
