package dev.yowkey.portfolio.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Debug builds only: how [DebugApi]'s stand-in is set. Declared in src/debug/AndroidManifest.xml behind
 * `android.permission.DUMP`, so only adb's shell reaches it, never another app on the phone.
 */
class DebugApiReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        DebugApi.take(context, intent.getStringExtra(DebugApi.EXTRA))
    }
}
