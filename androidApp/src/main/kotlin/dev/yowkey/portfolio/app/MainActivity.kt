package dev.yowkey.portfolio.app

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.yowkey.portfolio.app.owner.OwnerViewModel
import dev.yowkey.portfolio.app.ui.YowkeyRoot
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * The one activity. It opens on the guest view (the portfolio, as on the web) unless the owner is signed in, or a
 * shortcut or notification names a place in the admin view ([OwnerLink]). Drawn edge to edge; each screen keeps
 * clear of the system bars itself.
 */
class MainActivity : ComponentActivity() {

    private val owner: OwnerViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        DebugApi.capture(this, intent)
        if (savedInstanceState == null) owner.start(OwnerLink.from(intent))
        setContent {
            YowkeyRoot(owner, reducedMotion = reducedMotion())
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        DebugApi.capture(this, intent)
        OwnerLink.from(intent)?.let(owner::open)
    }

    /** Animations switched off in the developer options or accessibility settings: the page skips its reveals. */
    private fun reducedMotion(): Boolean =
        Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
}
