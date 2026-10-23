package dev.yowkey.portfolio.ui.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
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
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The note form under the Contact block: name and email (both optional), an overall impression out of five, the
 * message, Send. [state] is the ViewModel's: while a note is on its way the button waits; a failure's sentence
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
        Text(
            FeedbackCopy.TITLE,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { heading() },
        )
        Text(FeedbackCopy.INTRO, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (state is FeedbackUiState.Sent) SentNote(onReset) else DraftForm(state, onSend, wide)
    }
}

@Composable
private fun DraftForm(state: FeedbackUiState, onSend: (FeedbackDraft) -> Unit, wide: Boolean) {
    var name by rememberSaveable { mutableStateOf("") }
    var contact by rememberSaveable { mutableStateOf("") }
    var rating by rememberSaveable { mutableStateOf(0) }
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
        val shown = problem ?: (state as? FeedbackUiState.Failed)?.message
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(
                onClick = {
                    val draft = FeedbackDraft(name, contact, rating.takeIf { it in 1..5 }, message)
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

/** A five-pointed star, drawn (no icon font to download): filled when chosen, outlined when not. */
@Composable
private fun StarGlyph(filled: Boolean, color: Color, modifier: Modifier = Modifier) {
    Spacer(
        modifier.size(24.dp).drawWithCache {
            val star = starPath(size)
            val outline = Stroke(width = 1.6.dp.toPx(), join = StrokeJoin.Round)
            onDrawBehind { if (filled) drawPath(star, color) else drawPath(star, color, style = outline) }
        },
    )
}

private fun starPath(size: Size): Path {
    val c = size.center
    val outer = size.minDimension / 2
    val inner = outer * 0.42f
    return Path().apply {
        for (i in 0 until 10) {
            val r = if (i % 2 == 0) outer else inner
            val angle = -PI / 2 + i * PI / 5
            val x = c.x + r * cos(angle).toFloat()
            val y = c.y + r * sin(angle).toFloat()
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
}

@Composable
private fun SentNote(onReset: () -> Unit) {
    Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(FeedbackCopy.THANKS, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
        OutlinedButton(onClick = onReset) { Text(FeedbackCopy.ANOTHER) }
    }
}
