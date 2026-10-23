package dev.yowkey.portfolio.ui.theme

import java.util.prefs.Preferences

private val preferences: Preferences get() = Preferences.userRoot().node("dev/yowkey/portfolio")

actual fun savedDarkTheme(): Boolean? = when (preferences.get("theme", null)) {
    "dark" -> true
    "light" -> false
    else -> null
}

actual fun saveDarkTheme(dark: Boolean) {
    preferences.put("theme", if (dark) "dark" else "light")
}
