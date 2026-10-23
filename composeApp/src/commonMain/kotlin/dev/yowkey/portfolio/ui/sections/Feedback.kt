package dev.yowkey.portfolio.ui.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.yowkey.portfolio.model.FeedbackCopy
import dev.yowkey.portfolio.model.FeedbackDraft
import dev.yowkey.portfolio.ui.FeedbackUiState
import dev.yowkey.portfolio.ui.components.StarGlyph
import dev.yowkey.portfolio.ui.components.StatusBadge

/**
 * The public note form, after the published notes it may join: name and email (both optional), an overall
 * impression out of five, the message, the consent to show it, Send. [state] is the ViewModel's: while a note is on its way the button waits; a failure's sentence
 * shows beside it; once sent, the form gives way to a thank-you and a way to write another. The draft is saveable,
 * so scrolling the row off the screen and back keeps it; the thank-you need not be.
 */
@Composable
fun FeedbackForm(
    state: FeedbackUiState,
    onSend: (FeedbackDraft) -> Unit,
    onReset: () -> Unit,
    wide: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier.widthIn(max = 640.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        FormTitle(FeedbackCopy.TITLE, FeedbackCopy.PUBLIC)
        Text(FeedbackCopy.INTRO, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (state is FeedbackUiState.Sent) SentNote(onReset) else DraftForm(state, onSend, wide)
    }
}

/**
 * A form's title with who reads what is sent beside it ("Private" for a message, "Public" for a note), so the two
 * forms of the Contact section cannot be mistaken for each other. One heading node: "Message me, Private".
 */
@Composable
internal fun FormTitle(title: String, audience: String) {
    Row(
        Modifier.semantics(mergeDescendants = true) { heading() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
        StatusBadge(audience)
    }
}

@Composable
private fun DraftForm(state: FeedbackUiState, onSend: (FeedbackDraft) -> Unit, wide: Boolean) {
    var name by rememberSaveable { mutableStateOf("") }
    var contact by rememberSaveable { mutableStateOf("") }
    var rating by rememberSaveable { mutableStateOf(0) }
    var message by rememberSaveable { mutableStateOf("") }
    var consent by rememberSaveable { mutableStateOf(false) }
    // The form's own objection, raised at Send and dropped at the next keystroke; the server's comes in [state].
    var problem by remember { mutableStateOf<String?>(null) }
    val sending = state is FeedbackUiState.Sending

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val nameField: @Composable (Modifier) -> Unit = { fieldModifier ->
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(FeedbackDraft.MAX_NAME) },
                modifier = fieldModifier,
                label = { Text(FeedbackCopy.NAME) },
                placeholder = { Text("Optional") },
                singleLine = true,
                enabled = !sending,
            )
        }
        val contactField: @Composable (Modifier) -> Unit = { fieldModifier ->
            OutlinedTextField(
                value = contact,
                onValueChange = { contact = it.take(FeedbackDraft.MAX_CONTACT) },
                modifier = fieldModifier,
                label = { Text(FeedbackCopy.CONTACT) },
                placeholder = { Text("Optional, if you'd like a reply") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                enabled = !sending,
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
        RatingRow(rating, onRate = { rating = if (it == rating) 0 else it }, enabled = !sending)
        OutlinedTextField(
            value = message,
            onValueChange = {
                message = it.take(FeedbackDraft.MAX_MESSAGE)
                problem = null
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp),
            label = { Text(FeedbackCopy.MESSAGE) },
            supportingText = { Text("${message.length} / ${FeedbackDraft.MAX_MESSAGE}") },
            isError = problem != null && message.isBlank(),
            enabled = !sending,
        )
        // The whole line toggles, and reads as one checkbox.
        Row(
            Modifier
                .fillMaxWidth()
                .toggleable(value = consent, enabled = !sending, role = Role.Checkbox) { consent = it }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Checkbox(checked = consent, onCheckedChange = null, enabled = !sending)
            Text(FeedbackCopy.CONSENT, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        }
        val shown = problem ?: (state as? FeedbackUiState.Failed)?.message
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(
                onClick = {
                    val draft = FeedbackDraft(name, contact, rating.takeIf { it in 1..5 }, message, consent)
                    problem = draft.problems.firstOrNull()
                    if (problem == null) onSend(draft)
                },
                enabled = !sending,
            ) { Text(if (sending) FeedbackCopy.SENDING else FeedbackCopy.SEND) }
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
 * Five stars, one tap each: a radio group, so a screen reader hears "4 · Good, selected". Tapping the chosen star
 * again clears the rating; it is optional, after all.
 */
@Composable
private fun RatingRow(rating: Int, onRate: (Int) -> Unit, enabled: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(FeedbackCopy.RATING, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (star in 1..5) {
                val lit = star <= rating
                val label = FeedbackCopy.ratingLabel(star)
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .selectable(selected = star == rating, enabled = enabled, role = Role.RadioButton) { onRate(star) }
                        .semantics { contentDescription = label },
                    contentAlignment = Alignment.Center,
                ) {
                    StarGlyph(filled = lit, color = if (lit) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
}

@Composable
private fun SentNote(onReset: () -> Unit) {
    Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(FeedbackCopy.THANKS, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
        OutlinedButton(onClick = onReset) { Text(FeedbackCopy.ANOTHER) }
    }
}
