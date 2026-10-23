package dev.yowkey.portfolio.app

import android.content.Context
import android.content.Intent
import androidx.core.content.edit

/**
 * Debug builds only (release has a no-op twin): sends every request meant for the worker to a local stand-in
 * instead, so the admin view can be tried without the real token and the guest view counts nothing live. Set it
 * with an intent extra, which sticks until cleared:
 *
 * `adb shell am start -n dev.yowkey.portfolio/.app.MainActivity --es debug_api http://10.0.2.2:8897`
 *
 * and `--es debug_api ""` goes back to the real worker. Plain HTTP is allowed only to 10.0.2.2 and localhost.
 */
object DebugApi {
    private const val EXTRA = "debug_api"
    private const val KEY = "api"
    private val Origin = Regex("^https?://[^/]+")

    fun capture(context: Context, intent: Intent?) {
        val value = intent?.getStringExtra(EXTRA) ?: return
        prefs(context).edit(commit = true) { if (value.isBlank()) remove(KEY) else putString(KEY, value.trim().trimEnd('/')) }
    }

    fun rewriter(context: Context): (String) -> String {
        val prefs = prefs(context)
        return { url -> prefs.getString(KEY, null)?.let { base -> url.replaceFirst(Origin, base) } ?: url }
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("debug", Context.MODE_PRIVATE)
}
