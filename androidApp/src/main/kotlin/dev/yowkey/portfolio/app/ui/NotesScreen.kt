package dev.yowkey.portfolio.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.yowkey.portfolio.admin.AdminNote
import dev.yowkey.portfolio.admin.NoteAction
import dev.yowkey.portfolio.app.owner.OwnerViewModel
import dev.yowkey.portfolio.app.owner.Tab
import java.time.ZoneId

/**
 * The visitors' notes, newest first, with the inbox's buttons. A note is on the site as soon as it arrives, so the
 * levers are Hide (off the site, and its stars stop counting) and Delete. A note a notification named is scrolled to
 * and outlined.
 */
@Composable
fun NotesScreen(owner: OwnerViewModel) {
    val state by owner.notes.collectAsState()
    val nav by owner.nav.collectAsState()
    val zone by owner.zone.collectAsState()
    val working by owner.working.collectAsState()
    LoadableContent(
        state = state,
        onRefresh = { owner.refresh(Tab.Notes) },
        isEmpty = { it.isEmpty() },
        emptyText = "No notes yet. When a visitor leaves one on the site, it lands here.",
    ) { notes ->
        val list = rememberLazyListState()
        val focus = nav.noteId
        LaunchedEffect(focus, notes) {
            val index = notes.indexOfFirst { it.id == focus }
            if (focus != null && index >= 0) list.animateScrollToItem(index)
        }
        LazyColumn(
            state = list,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(notes, key = { it.id }) { note ->
                Centered {
                    NoteCard(note, zone, focused = note.id == focus, busy = note.id in working, onAct = { owner.act(note, it) })
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NoteCard(note: AdminNote, zone: ZoneId, focused: Boolean, busy: Boolean, onAct: (NoteAction) -> Unit) {
    var confirmDelete by remember { mutableStateOf(false) }
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        border = if (focused) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(note.name?.takeIf { it.isNotBlank() } ?: "Someone", style = MaterialTheme.typography.titleMedium)
                note.rating?.let {
                    Text("  ${stars(it)}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.weight(1f))
                Text(whenText(note.createdAt, zone), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                when {
                    note.isHidden -> Tag("Hidden")
                    note.isPublic -> Positive.let { (container, content) -> Tag("On the site", container, content) }
                    else -> Tag("Private: only its stars count")
                }
            }
            SelectionContainer {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(note.message, style = MaterialTheme.typography.bodyLarge)
                    note.contact?.takeIf { it.isNotBlank() }?.let {
                        Text("Contact: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (note.isHidden) {
                    OutlinedButton(onClick = { onAct(NoteAction.Unhide) }, enabled = !busy) { Text("Unhide") }
                } else {
                    OutlinedButton(onClick = { onAct(NoteAction.Hide) }, enabled = !busy) { Text("Hide") }
                }
                TextButton(onClick = { confirmDelete = true }, enabled = !busy) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            }
            if (!note.consent) {
                Text(
                    "Sent before notes were public, without the visitor's tick: its words stay private, its stars count.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete this note?",
            text = "It is removed from the server, and from the site if it was there, for good.",
            confirm = "Delete",
            onConfirm = { onAct(NoteAction.Delete) },
            onDismiss = { confirmDelete = false },
        )
    }
}
