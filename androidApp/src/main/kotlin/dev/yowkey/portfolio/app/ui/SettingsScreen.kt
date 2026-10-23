package dev.yowkey.portfolio.app.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import dev.yowkey.portfolio.BuildConfig
import dev.yowkey.portfolio.app.owner.OwnerViewModel
import dev.yowkey.portfolio.ui.theme.AndroidThemeStore
import dev.yowkey.portfolio.ui.theme.saveDarkTheme

/**
 * Notifications (the permission, asked for here again if needed), push on both ends, the backup check, the way
 * back to the portfolio, and signing out.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(owner: OwnerViewModel) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val dashboard by owner.dashboard.collectAsState()
    val dark by AndroidThemeStore.dark.collectAsState()
    // Re-read whenever the screen comes back (from the system's notification settings, say).
    var resumes by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumes++ }
    val push = remember(resumes) { owner.pushInfo() }
    var denied by remember { mutableStateOf(false) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        owner.askedForNotifications()
        denied = !granted
        resumes++
    }
    var confirmSignOut by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Centered {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Section("Notifications") {
                    Line(if (push.notificationsAllowed) "Allowed: new messages, notes and the daily summary show up here." else "Not allowed, so no alert can show.")
                    if (!push.notificationsAllowed) {
                        val blocked = denied && activity != null && !canAskAgain(activity)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !blocked) {
                            Button(onClick = { ask.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text("Allow notifications") }
                        } else {
                            Button(onClick = { openNotificationSettings(context) }) { Text("Open notification settings") }
                        }
                    }
                }
                Section("Push") {
                    Line(
                        if (push.firebase) {
                            if (push.registered) "This phone: registered with the server." else "This phone: not registered yet; it is sent once online."
                        } else {
                            "This build has no Firebase config, so push is off here. Alerts come by the backup check."
                        },
                    )
                    dashboard.value?.summary?.push?.let { server ->
                        Line(
                            when {
                                !server.configured -> "Server: push not set up (no FCM_SERVICE_ACCOUNT secret)."
                                else -> "Server: push set up, ${count(server.devices, "phone")} registered."
                            },
                        )
                    }
                }
                Section("Backup check") {
                    Line("Every 15 minutes while signed in and online, the app asks the server for new alerts, so none is missed when a push is not delivered.")
                    OutlinedButton(onClick = owner::checkNow) { Text("Check now") }
                }
                Section("This app") {
                    Line("Yowkey ${BuildConfig.VERSION_NAME}. The portfolio is the guest view; this owner view needs the password.")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Dark theme", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        // The same choice as the page's own switch, kept for both views.
                        Switch(checked = dark, onCheckedChange = { saveDarkTheme(it) })
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = owner::showPortfolio) { Text("View portfolio") }
                        OutlinedButton(onClick = { confirmSignOut = true }) { Text("Sign out", color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }
    }
    if (confirmSignOut) {
        ConfirmDialog(
            title = "Sign out?",
            text = "This phone stops getting alerts and forgets the password. The portfolio stays.",
            confirm = "Sign out",
            onConfirm = owner::signOut,
            onDismiss = { confirmSignOut = false },
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun Line(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** After a refusal, Android only asks again while it would show a rationale; past that only Settings can allow it. */
private fun canAskAgain(activity: Activity): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && activity.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)

private fun openNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}
