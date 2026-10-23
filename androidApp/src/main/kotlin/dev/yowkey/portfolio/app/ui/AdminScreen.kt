package dev.yowkey.portfolio.app.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.yowkey.portfolio.R
import dev.yowkey.portfolio.app.owner.OwnerViewModel
import dev.yowkey.portfolio.app.owner.Tab

/**
 * The admin view: a navigation bar on a phone, a rail from 600 dp (a tablet, or a phone on its side), the section
 * under a top bar that also leads back to the portfolio. System Back closes a message, then goes to the dashboard,
 * then leaves.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(owner: OwnerViewModel) {
    val nav by owner.nav.collectAsState()
    val dashboard by owner.dashboard.collectAsState()
    val unread = dashboard.value?.summary?.messages?.unread ?: 0
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(owner) { owner.notices.collect { snackbar.showSnackbar(it) } }
    AskForNotificationsOnce(owner)
    BackHandler(enabled = nav.messageId != null || nav.tab != Tab.Dashboard) { owner.back() }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 600.dp
        val twoPane = wideDetail(maxWidth.value)
        val short = maxHeight < 480.dp
        Row(Modifier.fillMaxSize()) {
            if (wide) {
                // A phone on its side is short: five labelled items and the mark would not fit, so it keeps the
                // icons (the selected one labelled) and drops the mark.
                val mark: (@Composable ColumnScope.() -> Unit)? =
                    if (short) null else { { Box(Modifier.padding(vertical = 12.dp)) { YMark(40) } } }
                NavigationRail(header = mark) {
                    if (!short) Spacer(Modifier.height(8.dp))
                    Tab.entries.forEach { tab ->
                        NavigationRailItem(
                            selected = nav.tab == tab,
                            onClick = { owner.select(tab) },
                            icon = { TabIcon(tab, unread) },
                            label = { TabLabel(tab) },
                            alwaysShowLabel = !short,
                            colors = NavigationRailItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primaryContainer),
                        )
                    }
                }
            }
            Scaffold(
                modifier = Modifier.weight(1f),
                topBar = {
                    val title = if (nav.tab == Tab.Messages && nav.messageId != null && !twoPane) "Message" else nav.tab.label
                    TopAppBar(
                        title = { Text(title) },
                        navigationIcon = {
                            if (nav.messageId != null && !twoPane) {
                                IconButton(onClick = owner::closeMessage) {
                                    Icon(painterResource(R.drawable.ic_back), contentDescription = "Back to the messages")
                                }
                            }
                        },
                        actions = { TextButton(onClick = owner::showPortfolio) { Text("Portfolio") } },
                        windowInsets = if (wide) TopAppBarDefaults.windowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.End) else TopAppBarDefaults.windowInsets,
                    )
                },
                bottomBar = {
                    if (!wide) {
                        NavigationBar {
                            Tab.entries.forEach { tab ->
                                NavigationBarItem(
                                    selected = nav.tab == tab,
                                    onClick = { owner.select(tab) },
                                    icon = { TabIcon(tab, unread) },
                                    label = { TabLabel(tab) },
                                    colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primaryContainer),
                                )
                            }
                        }
                    }
                },
                snackbarHost = { SnackbarHost(snackbar) },
                contentWindowInsets = if (wide) WindowInsets.safeDrawing.only(WindowInsetsSides.End + WindowInsetsSides.Bottom) else WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
            ) { padding ->
                Box(Modifier.padding(padding).fillMaxSize()) {
                    when (nav.tab) {
                        Tab.Dashboard -> DashboardScreen(owner)
                        Tab.Messages -> MessagesScreen(owner, twoPane = twoPane)
                        Tab.Notes -> NotesScreen(owner)
                        Tab.Records -> RecordsScreen(owner)
                        Tab.Settings -> SettingsScreen(owner)
                    }
                }
            }
        }
    }
}

/** The page's labelMedium is an eyebrow, widely spaced; the bar's labels are words, so they drop the spacing. */
@Composable
private fun TabLabel(tab: Tab) {
    Text(tab.label, maxLines = 1, style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.sp))
}

/** Messages shows the list and the open message side by side from 840 dp. */
private fun wideDetail(widthDp: Float): Boolean = widthDp >= 840f

@Composable
private fun TabIcon(tab: Tab, unread: Int) {
    if (tab == Tab.Messages && unread > 0) {
        BadgedBox(badge = { Badge { Text(if (unread > 99) "99+" else unread.toString()) } }) {
            Icon(painterResource(tab.icon), contentDescription = null)
        }
    } else {
        Icon(painterResource(tab.icon), contentDescription = null)
    }
}

/**
 * Right after the first sign-in on Android 13+, asks once for the notification permission, which push and the
 * backup check need. Settings shows its state afterwards and can ask again.
 */
@Composable
private fun AskForNotificationsOnce(owner: OwnerViewModel) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (owner.shouldAskForNotifications()) {
            owner.askedForNotifications()
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
