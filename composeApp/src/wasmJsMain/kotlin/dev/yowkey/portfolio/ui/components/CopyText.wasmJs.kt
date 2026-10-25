@file:OptIn(ExperimentalWasmJsInterop::class)

package dev.yowkey.portfolio.ui.components

import androidx.compose.runtime.Composable

/** The async Clipboard API; the click that calls it is the user activation it needs. */
private fun writeClipboard(text: String): Unit = js(
    "(function () { try { navigator.clipboard.writeText(text).catch(function () {}); } catch (e) {} })()",
)

@Composable
internal actual fun rememberTextCopier(): (String) -> Unit = ::writeClipboard
