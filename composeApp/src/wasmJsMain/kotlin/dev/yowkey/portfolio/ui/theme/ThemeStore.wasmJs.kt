@file:OptIn(ExperimentalWasmJsInterop::class)

package dev.yowkey.portfolio.ui.theme

/** The saved "theme" key, or "" when there is none or storage is off (a private window, say). */
private fun readTheme(): String = js(
    "(function () { try { return localStorage.getItem('theme') || ''; } catch (e) { return ''; } })()",
)

/**
 * Saves the key and applies the theme to the page around the canvas through the template's `applyTheme` (the
 * `data-theme` attribute the static page's CSS keys on, and the theme-color meta the browser's chrome takes).
 * Without storage the choice still lasts the visit.
 */
private fun writeTheme(theme: String): Unit = js(
    """(function () {
        try { localStorage.setItem('theme', theme); } catch (e) {}
        if (typeof window.applyTheme === 'function') window.applyTheme(theme);
    })()""",
)

actual fun savedDarkTheme(): Boolean? = when (readTheme()) {
    "dark" -> true
    "light" -> false
    else -> null
}

actual fun saveDarkTheme(dark: Boolean) = writeTheme(if (dark) "dark" else "light")
