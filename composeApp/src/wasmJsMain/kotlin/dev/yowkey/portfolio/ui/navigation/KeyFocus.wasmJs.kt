@file:OptIn(ExperimentalWasmJsInterop::class)

package dev.yowkey.portfolio.ui.navigation

/**
 * Waits (a few frames at most) for the app's text area to let go, then, if the focus fell to the body, gives it to
 * the canvas; a focus anywhere else is the visitor's to keep. The canvas is found as `markAppReady` in main.kt
 * finds it.
 */
internal actual fun returnKeysToPage(): Unit = js(
    """(function () {
        var app = document.getElementById('app');
        if (!app) return;
        var host = Array.prototype.find.call(app.querySelectorAll('*'), function (e) { return e.shadowRoot; });
        var canvas = host ? host.shadowRoot.querySelector('canvas') : app.querySelector('canvas');
        if (!canvas) return;
        var frames = 0;
        (function check() {
            var active = document.activeElement;
            if (!active || active === document.body) { canvas.focus({ preventScroll: true }); return; }
            var inner = host && active === host ? host.shadowRoot.activeElement : null;
            if (inner && inner !== canvas && ++frames < 10) window.requestAnimationFrame(check);
        })();
    })()""",
)
