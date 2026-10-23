package dev.yowkey.portfolio.ui.theme

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Android keeps the theme choice in the app's private preferences ("theme": "light" or "dark", as on the web). The
 * app hands over its context once, at start ([init]); before that nothing is saved and the page is light. [dark]
 * lets the app around the shared page follow a switch made inside it: the system bars' icons, the admin view.
 */
object AndroidThemeStore {
    private const val KEY = "theme"

    @Volatile
    private var prefs: SharedPreferences? = null
    private val _dark = MutableStateFlow(false)

    /** The theme in use: the saved choice, light when there is none. */
    val dark: StateFlow<Boolean> = _dark.asStateFlow()

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences("portfolio", Context.MODE_PRIVATE)
        _dark.value = read() ?: false
    }

    internal fun read(): Boolean? = when (prefs?.getString(KEY, null)) {
        "dark" -> true
        "light" -> false
        else -> null
    }

    internal fun write(dark: Boolean) {
        prefs?.edit()?.putString(KEY, if (dark) "dark" else "light")?.apply()
        _dark.value = dark
    }
}

actual fun savedDarkTheme(): Boolean? = AndroidThemeStore.read()

actual fun saveDarkTheme(dark: Boolean) = AndroidThemeStore.write(dark)
