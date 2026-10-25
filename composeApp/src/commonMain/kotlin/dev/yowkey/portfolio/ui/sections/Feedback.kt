package dev.yowkey.portfolio.ui.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.yowkey.portfolio.model.FeedbackCopy
import dev.yowkey.portfolio.model.FeedbackDraft
import dev.yowkey.portfolio.model.Relation
import dev.yowkey.portfolio.ui.FeedbackUiState
import dev.yowkey.portfolio.ui.components.StarGlyph
import dev.yowkey.portfolio.ui.components.StatusBadge
import dev.yowkey.portfolio.ui.theme.ControlOutline

/**
 * The Feedback section, under the notes a note joins as soon as it is sent: its title with the "Public" badge, who it
 * is for, and the button that opens the form over the page ([FeedbackDialog]). Outlined, where the message's button is
 * filled: the private message is the section's way to reach the owner, a public note the lighter thing to do.
 */
@Composable
fun FeedbackSection(onOpen: () -> Unit, modifier: Modifier = Modifier, opener: FocusRequester? = null) {
    FormSection(
        FeedbackCopy.TITLE, FeedbackCopy.PUBLIC, FeedbackCopy.INTRO, FeedbackCopy.OPEN,
        primary = false, onOpen = onOpen, opener = opener, modifier = modifier,
    )
}

/**
 * The note form over the page: what happens to a note ([FeedbackCopy.FORM_INTRO]) before anyone writes one, then
 * the form; the thank-you, once it is sent, replaces both. [drafts] keeps the draft while the dialog is closed, so it is there again when the dialog reopens. Once a
 * note is sent the thank-you shows here; "Write another", or closing the dialog on the thank-you ([onClose]), goes
 * back to the empty form ([onReset]) and forgets the sent draft, so the next note starts empty.
 */
@Composable
fun FeedbackDialog(
    state: FeedbackUiState,
    onSend: (FeedbackDraft) -> Unit,
    onReset: () -> Unit,
    wide: Boolean,
    onClose: () -> Unit,
    drafts: SaveableStateHolder = rememberSaveableStateHolder(),
) {
    val reset = {
        drafts.removeState(DRAFT_KEY)
        onReset()
    }
    FormDialog(
        FeedbackCopy.TITLE, FeedbackCopy.PUBLIC, FeedbackCopy.CLOSE,
        onClose = {
            if (state is FeedbackUiState.Sent) reset()
            onClose()
        },
    ) {
        // About the form, so it goes with it: the thank-you stands alone.
        if (state !is FeedbackUiState.Sent) {
            val intro = MaterialTheme.typography.bodyMedium
            Text(FeedbackCopy.FORM_INTRO, style = intro, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.readingMeasure(intro))
        }
        FeedbackForm(state, onSend, reset, wide, drafts = drafts)
    }
}

/**
 * The public note form: name and email (both optional, the email never shown), how the writer knows the owner
 * (optional, shown on the note as a badge), an overall impression out of five, the message, Send. [state] is the
 * ViewModel's: while a note is on its way the button waits; a failure's sentence shows beside it; once sent, the form
 * gives way to a thank-you and a way to write another. The draft's fields are saveable, kept in [drafts] while the
 * form is not on screen (its dialog closed, or the thank-you up).
 */
@Composable
fun FeedbackForm(
    state: FeedbackUiState,
    onSend: (FeedbackDraft) -> Unit,
    onReset: () -> Unit,
    wide: Boolean,
    modifier: Modifier = Modifier,
    drafts: SaveableStateHolder = rememberSaveableStateHolder(),
) {
    Box(modifier) {
        if (state is FeedbackUiState.Sent) SentNote(onReset)
        else drafts.SaveableStateProvider(DRAFT_KEY) { DraftForm(state, onSend, wide) }
    }
}

/**
 * A form's title with who reads what is sent beside it ("Private" for a message, "Public" for a note), so the two
 * forms of the Contact section cannot be mistaken for each other. One heading node: "Message me, Private".
 */
@Composable
internal fun FormTitle(title: String, audience: String, modifier: Modifier = Modifier) {
    Row(
        modifier.semantics(mergeDescendants = true) { heading() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        StatusBadge(audience)
    }
}

/** The forms' text fields: at rest each is edged in [ControlOutline], so an empty one still shows where it is. */
@Composable
internal fun formFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = ControlOutline)

@Composable
private fun DraftForm(state: FeedbackUiState, onSend: (FeedbackDraft) -> Unit, wide: Boolean) {
    var name by rememberSaveable { mutableStateOf("") }
    var contact by rememberSaveable { mutableStateOf("") }
    var rating by rememberSaveable { mutableStateOf(0) }
    // The relation's wire value, so it saves as a plain string; empty for not saying.
    var relation by rememberSaveable { mutableStateOf("") }
    var message by rememberSaveable { mutableStateOf("") }
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
                colors = formFieldColors(),
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
        RelationChips(relation, onPick = { relation = if (it.wire == relation) "" else it.wire }, enabled = !sending)
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
            colors = formFieldColors(),
        )
        val shown = problem ?: (state as? FeedbackUiState.Failed)?.message
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(
                onClick = {
                    val draft = FeedbackDraft(name, contact, rating.takeIf { it in 1..5 }, message, relation = relation)
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
 * "How do you know me?": the four relations as chips, one choice at a time, wrapping on a phone, as the message
 * form's topics. A radio group, so a screen reader hears "Colleague, selected"; tapping the chosen one again clears
 * it, since saying is optional.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RelationChips(relation: String, onPick: (Relation) -> Unit, enabled: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(FeedbackCopy.RELATION, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(
            Modifier.selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Relation.offered.forEach { choice ->
                FilterChip(
                    selected = choice.wire == relation,
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

/**
 * Five stars, one tap each: a radio group, so a screen reader hears "4 · Good, selected". Tapping the chosen star
 * again clears the rating; it is optional, after all. Each star is a 48 dp target, and an unlit one is drawn in
 * [ControlOutline], since its outline is all that shows the control is there.
 */
@Composable
private fun RatingRow(rating: Int, onRate: (Int) -> Unit, enabled: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(FeedbackCopy.RATING, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.selectableGroup()) {
            for (star in 1..5) {
                val lit = star <= rating
                val label = FeedbackCopy.ratingLabel(star)
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .selectable(selected = star == rating, enabled = enabled, role = Role.RadioButton) { onRate(star) }
                        .semantics { contentDescription = label },
                    contentAlignment = Alignment.Center,
                ) {
                    StarGlyph(filled = lit, color = if (lit) MaterialTheme.colorScheme.primary else ControlOutline)
                }
            }
        }
    }
}

@Composable
private fun SentNote(onReset: () -> Unit) {
    Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(FeedbackCopy.THANKS, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        OutlinedButton(onClick = onReset) { Text(FeedbackCopy.ANOTHER) }
    }
}

/** Where [FeedbackDialog] keeps the draft in its [SaveableStateHolder] while it is closed. */
private const val DRAFT_KEY = "feedback-draft"
