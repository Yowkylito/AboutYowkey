package dev.yowkey.portfolio.app

import android.content.Context
import androidx.core.content.edit

/**
 * Debug builds only (release has a no-op twin): sends every request meant for the worker to a local stand-in
 * instead, so the admin view can be tried without the real token and the guest view counts nothing live. Set it
 * from the computer, with a broadcast to [DebugApiReceiver], and it sticks until cleared:
 *
 * `adb shell am broadcast -n dev.yowkey.portfolio/.app.DebugApiReceiver --es debug_api http://10.0.2.2:8897`
 *
 * and `--es debug_api ""` goes back to the real worker. The owner's token goes wherever the requests go, so the
 * stand-in is never taken from an app on the phone: the receiver answers only senders holding
 * `android.permission.DUMP`, which adb's shell has and an installed app cannot get by asking, and the launcher's
 * activity, which any app may start, reads no such extra. Any app can listen on this device's loopback, so who may
 * name it matters. Only plain HTTP to 10.0.2.2 or this device ([standIn]) is taken even then.
 */
object DebugApi {
    const val EXTRA = "debug_api"
    private const val KEY = "api"
    private val Origin = Regex("^https?://[^/]+")
    /** The hosts the debug build's network security config lets plain HTTP reach, with an optional port. */
    private val LocalOrigin = Regex("^http://(10\\.0\\.2\\.2|localhost|127\\.0\\.0\\.1)(:\\d{1,5})?$")

    /** Keeps [value] as the stand-in when it is a local one, clears it when blank, and ignores anything else. */
    fun take(context: Context, value: String?) {
        value ?: return
        val base = standIn(value)
        if (value.isNotBlank() && base == null) return
        prefs(context).edit(commit = true) { if (base == null) remove(KEY) else putString(KEY, base) }
    }

    fun rewriter(context: Context): (String) -> String {
        val prefs = prefs(context)
        return { url -> prefs.getString(KEY, null)?.let(::standIn)?.let { base -> url.replaceFirst(Origin, base) } ?: url }
    }

    /** [value] as a stand-in's origin, without a trailing slash, when it is a local one; null for anything else. */
    internal fun standIn(value: String): String? = value.trim().trimEnd('/').takeIf(LocalOrigin::matches)

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("debug", Context.MODE_PRIVATE)
}
