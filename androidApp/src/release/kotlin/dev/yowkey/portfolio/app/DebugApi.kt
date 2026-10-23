package dev.yowkey.portfolio.app

import android.content.Context
import android.content.Intent

/** Release builds always talk to the real worker; the debug build's twin can point elsewhere. */
object DebugApi {
    @Suppress("UNUSED_PARAMETER")
    fun capture(context: Context, intent: Intent?) = Unit

    @Suppress("UNUSED_PARAMETER")
    fun rewriter(context: Context): (String) -> String = { it }
}
