package dev.yowkey.portfolio.app.owner

import android.content.Context
import androidx.core.content.edit
import dev.yowkey.portfolio.admin.rememberShown
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * What the app keeps about the owner, in private preferences: the token (sealed by [TokenCipher]; never logged,
 * never in plain text on disk), where the backup check stands, which alerts are up, and the push token the worker
 * was told about. Everything but [askedForNotifications] goes on sign-out.
 */
class OwnerStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("owner", Context.MODE_PRIVATE)
    private val cipher = TokenCipher()
    private val _signedIn = MutableStateFlow(prefs.contains(KEY_TOKEN))

    /** Whether a token is kept. It turns false on sign-out and when the worker stops accepting the token. */
    val signedIn: StateFlow<Boolean> = _signedIn.asStateFlow()

    /** The token, or null when signed out (or when the Keystore key is gone, which then signs out). */
    fun token(): String? {
        val sealed = prefs.getString(KEY_TOKEN, null) ?: return null
        return try {
            cipher.open(sealed)
        } catch (e: Exception) {
            signOut()
            null
        }
    }

    fun saveToken(token: String) {
        prefs.edit(commit = true) { putString(KEY_TOKEN, cipher.seal(token)) }
        _signedIn.value = true
    }

    /** Forgets the owner: the token, its key, the check's place and the push token. */
    fun signOut() {
        prefs.edit(commit = true) {
            remove(KEY_TOKEN)
            remove(KEY_CURSOR)
            remove(KEY_SHOWN)
            remove(KEY_PUSH_TOKEN)
        }
        cipher.forget()
        _signedIn.value = false
    }

    /** The newest alert id the backup check has handled; null before its first run. */
    var cursor: Long?
        get() = prefs.getLong(KEY_CURSOR, -1).takeIf { it >= 0 }
        set(value) = prefs.edit { if (value == null) remove(KEY_CURSOR) else putLong(KEY_CURSOR, value) }

    /** The alerts already put up (by a push or by the check), newest last. */
    val shown: List<Long>
        get() = prefs.getString(KEY_SHOWN, null).orEmpty().split(',').mapNotNull { it.toLongOrNull() }

    @Synchronized
    fun markShown(id: Long) {
        prefs.edit { putString(KEY_SHOWN, rememberShown(shown, id).joinToString(",")) }
    }

    /** The FCM token the worker was last told about. */
    var pushToken: String?
        get() = prefs.getString(KEY_PUSH_TOKEN, null)
        set(value) = prefs.edit { if (value == null) remove(KEY_PUSH_TOKEN) else putString(KEY_PUSH_TOKEN, value) }

    /** Whether the app has asked for the notification permission once already (Android 13+). */
    var askedForNotifications: Boolean
        get() = prefs.getBoolean(KEY_ASKED, false)
        set(value) = prefs.edit { putBoolean(KEY_ASKED, value) }

    private companion object {
        const val KEY_TOKEN = "token"
        const val KEY_CURSOR = "alerts.cursor"
        const val KEY_SHOWN = "alerts.shown"
        const val KEY_PUSH_TOKEN = "push.token"
        const val KEY_ASKED = "notifications.asked"
    }
}
