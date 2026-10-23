package dev.yowkey.portfolio.app.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.yowkey.portfolio.admin.AdminMessage
import dev.yowkey.portfolio.admin.MessageAction
import dev.yowkey.portfolio.admin.MessageFilter
import dev.yowkey.portfolio.app.owner.OwnerViewModel
import dev.yowkey.portfolio.app.owner.Tab
import java.time.ZoneId

/**
 * The private messages: filters, the list, and the open one (beside the list from 840 dp, over it below). Opening
 * a message marks it read.
 */
@Composable
fun MessagesScreen(owner: OwnerViewModel, twoPane: Boolean) {
    val state by owner.messages.collectAsState()
    val filter by owner.filter.collectAsState()
    val nav by owner.nav.collectAsState()
    val zone by owner.zone.collectAsState()
    val working by owner.working.collectAsState()
    val all = state.value.orEmpty()
    val open = nav.messageId?.let { id -> all.firstOrNull { it.id == id } }

    val list: @Composable (Modifier) -> Unit = { modifier ->
        Column(modifier) {
            Filters(all, filter, owner::setFilter)
            LoadableContent(
                state = state,
                onRefresh = { owner.refresh(Tab.Messages) },
                isEmpty = { messages -> messages.none(filter::matches) },
                emptyText = when (filter) {
                    MessageFilter.All -> "No messages yet. When a visitor writes from the site, it lands here."
                    MessageFilter.Hiring -> "Nobody has written about a job or freelance work yet."
                    MessageFilter.Unread -> "All read."
                    MessageFilter.Archived -> "Nothing archived."
                },
            ) { messages ->
                LazyColumn(Modifier.fillMaxSize()) {
                    items(messages.filter(filter::matches), key = { it.id }) { message ->
                        MessageRow(message, zone, selected = message.id == nav.messageId, onClick = { owner.openMessage(message.id) })
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }

    when {
        twoPane -> Row(Modifier.fillMaxSize()) {
            list(Modifier.weight(0.42f).fillMaxHeight())
            VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Box(Modifier.weight(0.58f).fillMaxHeight()) {
                if (open != null) {
                    MessageDetail(open, zone, busy = open.id in working, onAct = { owner.act(open, it) }, onNotice = owner::notice)
                } else {
                    Message(if (nav.messageId != null && state.loading) "Opening…" else "Pick a message to read it.")
                }
            }
        }
        open != null -> MessageDetail(open, zone, busy = open.id in working, onAct = { owner.act(open, it) }, onNotice = owner::notice)
        nav.messageId != null && state.value != null -> {
            // A notification named a message that is gone (deleted elsewhere): say so, and offer the list.
            Message("That message is no longer here.", action = "All messages", onAction = owner::closeMessage)
        }
        else -> list(Modifier.fillMaxSize())
    }
}

@Composable
private fun Filters(all: List<AdminMessage>, selected: MessageFilter, onSelect: (MessageFilter) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MessageFilter.entries.forEach { filter ->
            val n = all.count(filter::matches)
            FilterChip(
                selected = filter == selected,
                onClick = { onSelect(filter) },
                label = { Text(if (n > 0 && filter != MessageFilter.Archived) "${filter.label} · $n" else filter.label) },
            )
        }
    }
}

@Composable
private fun MessageRow(message: AdminMessage, zone: ZoneId, selected: Boolean, onClick: () -> Unit) {
    val unread = !message.isRead
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        colors = ListItemDefaults.colors(
            containerColor = if (selected) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.background,
        ),
        leadingContent = {
            Box(Modifier.size(10.dp).background(if (unread) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent, CircleShape))
        },
        overlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TopicTag(message)
                Text(whenText(message.createdAt, zone), maxLines = 1)
            }
        },
        headlineContent = {
            Text(message.sender, fontWeight = if (unread) FontWeight.Bold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = { Text(message.message, maxLines = 2, overflow = TextOverflow.Ellipsis) },
    )
}

@Composable
private fun TopicTag(message: AdminMessage) {
    if (message.isHiring) {
        Tag(message.topicLabel, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
    } else {
        Tag(message.topicLabel)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MessageDetail(message: AdminMessage, zone: ZoneId, busy: Boolean, onAct: (MessageAction) -> Unit, onNotice: (String) -> Unit) {
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Centered {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TopicTag(message)
                    if (message.isArchived) Tag("Archived")
                    Text(whenText(message.createdAt, zone), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(message.sender, style = MaterialTheme.typography.headlineSmall)
                SelectionContainer {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (message.contact.isNotBlank()) {
                            Text("Reply to: ${message.contact}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(message.message, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                Spacer(Modifier.height(4.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val email = message.replyEmail
                    if (email != null) {
                        Button(onClick = { reply(context, email, onNotice) }) { Text("Reply") }
                    } else if (message.contact.isNotBlank()) {
                        Button(onClick = { copy(context, message.contact); onNotice("Contact copied.") }) { Text("Copy contact") }
                    }
                    if (message.isArchived) {
                        OutlinedButton(onClick = { onAct(MessageAction.Unarchive) }, enabled = !busy) { Text("Unarchive") }
                    } else {
                        OutlinedButton(onClick = { onAct(MessageAction.Archive) }, enabled = !busy) { Text("Archive") }
                    }
                    if (message.isRead) {
                        TextButton(onClick = { onAct(MessageAction.Unread) }, enabled = !busy) { Text("Mark unread") }
                    }
                    TextButton(onClick = { confirmDelete = true }, enabled = !busy) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete this message?",
            text = "It is removed from the server for good.",
            confirm = "Delete",
            onConfirm = { onAct(MessageAction.Delete) },
            onDismiss = { confirmDelete = false },
        )
    }
}

/** Opens the mail app on a reply to [email]. */
private fun reply(context: Context, email: String, onNotice: (String) -> Unit) {
    val intent = Intent(Intent.ACTION_SENDTO, "mailto:".toUri())
        .putExtra(Intent.EXTRA_EMAIL, arrayOf(email))
        .putExtra(Intent.EXTRA_SUBJECT, "Re: your message on my portfolio")
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        copy(context, email)
        onNotice("No mail app here, so the address was copied.")
    }
}

private fun copy(context: Context, text: String) {
    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Contact", text))
}
