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
 *
 * Not while the reader is using the static page: scrolled down it (the app starts at the top and would throw them
 * back there), in one of its dialogs, or typing in one of its fields (a half-written message would close). Then the
 * hand-off waits, checked once a second, until they are back near the top with nothing open; a deep link to a project
 * hands off at once, since the app opens that project itself.
 */
private fun markAppReady(): Unit = js(
    """(function () {
        var page = document.getElementById('static-scroll');
        function busy() {
            if (/^#\/projects\//.test(location.hash)) return false;
            var active = document.activeElement;
            var typing = active && /^(INPUT|TEXTAREA|SELECT)$/.test(active.tagName);
            return (page && page.scrollTop > window.innerHeight / 2) || !!document.querySelector('dialog[open]') || typing;
        }
        function handOff() {
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
        }
        if (!busy()) return handOff();
        var wait = setInterval(function () { if (!busy()) { clearInterval(wait); handOff(); } }, 1000);
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
