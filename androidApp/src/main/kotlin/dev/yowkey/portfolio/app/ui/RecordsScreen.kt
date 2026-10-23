package dev.yowkey.portfolio.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.yowkey.portfolio.R
import dev.yowkey.portfolio.admin.AdminNotification
import dev.yowkey.portfolio.admin.DailyReport
import dev.yowkey.portfolio.admin.NotificationKind
import dev.yowkey.portfolio.app.owner.OwnerViewModel
import dev.yowkey.portfolio.app.owner.Tab
import java.time.ZoneId

/**
 * The history behind the notifications: every alert the worker recorded (a tap goes where its notification does),
 * and the 23:59 daily reports.
 */
@Composable
fun RecordsScreen(owner: OwnerViewModel) {
    val nav by owner.nav.collectAsState()
    val alerts by owner.alerts.collectAsState()
    val reports by owner.reports.collectAsState()
    val zone by owner.zone.collectAsState()
    Column(Modifier.fillMaxSize()) {
        val selected = if (nav.dailyRecords) 1 else 0
        PrimaryTabRow(selectedTabIndex = selected) {
            listOf("Alerts", "Daily reports").forEachIndexed { index, label ->
                androidx.compose.material3.Tab(
                    selected = selected == index,
                    onClick = { owner.showDailyRecords(index == 1) },
                    text = { Text(label) },
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (selected == 0) {
            LoadableContent(
                state = alerts,
                onRefresh = { owner.refresh(Tab.Records) },
                isEmpty = { it.isEmpty() },
                emptyText = "No alerts yet. Each new note, message and daily summary is recorded here.",
            ) { records ->
                LazyColumn(Modifier.fillMaxSize()) {
                    items(records, key = { it.id }) { record ->
                        AlertRow(record, zone, onClick = { owner.openAlert(record) })
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        } else {
            LoadableContent(
                state = reports,
                onRefresh = { owner.refresh(Tab.Records) },
                isEmpty = { it.isEmpty() },
                emptyText = "No daily reports yet. The first is written tonight at 23:59.",
            ) { days ->
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(days, key = { it.day }) { report -> Centered { ReportCard(report) } }
                }
            }
        }
    }
}

@Composable
private fun AlertRow(record: AdminNotification, zone: ZoneId, onClick: () -> Unit) {
    val icon = when (record.kind) {
        NotificationKind.FEEDBACK -> R.drawable.ic_nav_notes
        NotificationKind.DAILY -> R.drawable.ic_nav_dashboard
        else -> R.drawable.ic_nav_messages
    }
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        leadingContent = { Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        headlineContent = { Text(record.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(record.body, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        trailingContent = { Text(whenText(record.createdAt, zone), style = MaterialTheme.typography.labelSmall) },
    )
}

@Composable
private fun ReportCard(report: DailyReport) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(dayText(report.day), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                Text(count(report.visits, "visit"), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }
            Text(
                listOf(
                    "LinkedIn ${report.clicks["linkedin"] ?: 0}",
                    "GitHub ${report.clicks["github"] ?: 0}",
                    count(report.notes, "note"),
                    count(report.messages, "message"),
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
