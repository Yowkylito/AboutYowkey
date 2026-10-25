package dev.yowkey.portfolio.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
internal actual fun rememberTextCopier(): (String) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { text -> runCatching { context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Email", text)) } }
    }
}
