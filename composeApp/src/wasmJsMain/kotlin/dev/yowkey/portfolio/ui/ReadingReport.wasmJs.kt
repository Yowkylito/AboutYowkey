@file:OptIn(ExperimentalWasmJsInterop::class)

package dev.yowkey.portfolio.ui

/** The page's script, when it is there and counting: without a backend it never defines the function. */
private fun tellThePage(section: String): Unit = js(
    """(function () {
        if (typeof window.yowkeyReading === 'function') window.yowkeyReading(section);
    })()""",
)

internal actual fun reportReading(section: String) = tellThePage(section)
