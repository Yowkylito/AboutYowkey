package dev.yowkey.portfolio.app

import android.content.Context

/** Release builds always talk to the real worker; the debug build's twin can point elsewhere. */
object DebugApi {
    @Suppress("UNUSED_PARAMETER")
    fun rewriter(context: Context): (String) -> String = { it }
}
