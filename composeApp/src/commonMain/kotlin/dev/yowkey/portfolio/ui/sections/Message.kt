package dev.yowkey.portfolio.ui.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.yowkey.portfolio.model.MessageCopy
import dev.yowkey.portfolio.model.MessageDraft
import dev.yowkey.portfolio.model.MessageTopic
import dev.yowkey.portfolio.ui.MessageUiState

/**
 * The private message form, first under the Contact block: name (optional), how to reach you (required), what it is
 * about (one of four chips, or none), the message, Send. Its title carries a "Private" badge and its intro says only
 * the owner reads it, against the note form's "Public" further down. [state] is the ViewModel's, as for the note
 * form: while a message is on its way the button waits; a failure's sentence shows beside it, read out when it
 * appears; once sent, the form gives way to a thank-you and "Send another". The draft is saveable, so scrolling the
 * row off the screen and back keeps it.
 */
@Composable
fun MessageForm(
    state: MessageUiState,
    onSend: (MessageDraft) -> Unit,
    onReset: () -> Unit,
    wide: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier.widthIn(max = 640.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        FormTitle(MessageCopy.TITLE, MessageCopy.PRIVATE)
        val intro = MaterialTheme.typography.bodyMedium
        Text(MessageCopy.INTRO, style = intro, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.readingMeasure(intro))
        if (state is MessageUiState.Sent) SentMessage(onReset) else MessageDraftForm(state, onSend, wide)
    }
}

@Composable
private fun MessageDraftForm(state: MessageUiState, onSend: (MessageDraft) -> Unit, wide: Boolean) {
    var name by rememberSaveable { mutableStateOf("") }
    var contact by rememberSaveable { mutableStateOf("") }
    // The topic's wire value, so it saves as a plain string; empty until a chip is picked.
    var topic by rememberSaveable { mutableStateOf("") }
    var message by rememberSaveable { mutableStateOf("") }
    // The form's own objection, raised at Send and dropped at the next keystroke; the server's comes in [state].
    var problem by remember { mutableStateOf<String?>(null) }
    val sending = state is MessageUiState.Sending

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val nameField: @Composable (Modifier) -> Unit = { fieldModifier ->
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(MessageDraft.MAX_NAME) },
                modifier = fieldModifier,
                label = { Text(MessageCopy.NAME) },
                placeholder = { Text("Optional") },
                singleLine = true,
                enabled = !sending,
                colors = formFieldColors(),
            )
        }
        val contactField: @Composable (Modifier) -> Unit = { fieldModifier ->
            OutlinedTextField(
                value = contact,
                onValueChange = {
                    contact = it.take(MessageDraft.MAX_CONTACT)
                    problem = null
                },
                modifier = fieldModifier,
                label = { Text(MessageCopy.CONTACT) },
                supportingText = { Text("Required: ${MessageCopy.CONTACT_HINT}") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                isError = problem != null && contact.isBlank(),
                enabled = !sending,
                colors = formFieldColors(),
            )
        }
        if (wide) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                nameField(Modifier.weight(1f))
                contactField(Modifier.weight(1f))
            }
        } else {
            nameField(Modifier.fillMaxWidth())
            contactField(Modifier.fillMaxWidth())
        }
        TopicChips(topic, onPick = { topic = it.wire }, enabled = !sending)
        OutlinedTextField(
            value = message,
            onValueChange = {
                message = it.take(MessageDraft.MAX_MESSAGE)
                problem = null
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp),
            label = { Text(MessageCopy.MESSAGE) },
            supportingText = { Text("${message.length} / ${MessageDraft.MAX_MESSAGE}") },
            isError = problem != null && message.isBlank(),
            enabled = !sending,
            colors = formFieldColors(),
        )
        val shown = problem ?: (state as? MessageUiState.Failed)?.message
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(
                onClick = {
                    val draft = MessageDraft(name, contact, topic, message)
                    problem = draft.problems.firstOrNull()
                    if (problem == null) onSend(draft)
                },
                enabled = !sending,
            ) { Text(if (sending) MessageCopy.SENDING else MessageCopy.SEND) }
            if (shown != null) {
                Text(
                    shown,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
    }
}

/**
 * The four topics as chips, one choice at a time, wrapping on a phone. A radio group, so a screen reader hears "A
 * job, selected"; picking another moves the choice, and with none picked the backend files it as "Something else".
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TopicChips(topic: String, onPick: (MessageTopic) -> Unit, enabled: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(MessageCopy.TOPIC, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(
            Modifier.selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MessageTopic.entries.forEach { choice ->
                FilterChip(
                    selected = choice.wire == topic,
                    onClick = { onPick(choice) },
                    label = { Text(choice.label) },
                    // Outermost, so it wins over the chip's own checkbox role.
                    modifier = Modifier.semantics { role = Role.RadioButton },
                    enabled = enabled,
                )
            }
        }
    }
}

@Composable
private fun SentMessage(onReset: () -> Unit) {
    Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(MessageCopy.THANKS, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
        OutlinedButton(onClick = onReset) { Text(MessageCopy.ANOTHER) }
    }
}
