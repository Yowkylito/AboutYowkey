package dev.yowkey.portfolio.ui.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.yowkey.portfolio.model.FeedbackCopy
import dev.yowkey.portfolio.model.PublicNote
import dev.yowkey.portfolio.ui.components.OutlinedCard
import dev.yowkey.portfolio.ui.components.PinGlyph
import dev.yowkey.portfolio.ui.components.StarRow
import dev.yowkey.portfolio.ui.components.WrapRow

/**
 * One row of the public notes, as cards under the ratings dashboard in the Contact section (which carries the
 * title), pinned ones first as the backend orders them.
 */
@Composable
fun NoteCards(notes: List<PublicNote>, columns: Int, modifier: Modifier = Modifier, showsPins: Boolean = true) {
    CardRow(columns, notes.size, modifier) { index, cardModifier -> NoteCard(notes[index], cardModifier, showsPins) }
}

/**
 * A note, on the page and in a star's sheet alike: who wrote it on the left and their stars on the right; under it
 * the day, then the badges (Pinned, and how the writer knows the owner, when that is a colleague, a friend or a
 * special someone); then the words. Read as one node, in that order. The badges wrap in a [WrapRow], so the card
 * row's equal-height measurement counts them.
 */
@Composable
internal fun NoteCard(note: PublicNote, modifier: Modifier = Modifier, showsPins: Boolean = true) {
    val colors = MaterialTheme.colorScheme
    OutlinedCard(modifier) {
        Column(Modifier.semantics(mergeDescendants = true) {}.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    note.displayName,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                note.rating?.let { StarRow(it, glyphSize = 14.dp) }
            }
            val badge = note.badge
            val dateLine = note.dateLine
            val pinned = note.pinned && showsPins
            if (dateLine != null || pinned || badge != null) {
                Spacer(Modifier.height(6.dp))
                WrapRow(horizontalSpacing = 8.dp, verticalSpacing = 6.dp) {
                    dateLine?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.2.sp),
                            color = colors.onSurfaceVariant,
                        )
                    }
                    if (pinned) PinnedBadge()
                    badge?.let { RelationBadge(it.label) }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(note.message, style = MaterialTheme.typography.bodyMedium, color = colors.onSurface)
        }
    }
}

/**
 * "Pinned", with its pin, in a tint of the accent: the owner's mark, set apart from the neutral relation badge.
 * onPrimaryContainer on a 12% accent tint over the card is 10.9:1 in light and 11.2:1 in dark, as on the tech chips.
 */
@Composable
private fun PinnedBadge() {
    val colors = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(6.dp), color = colors.primary.copy(alpha = 0.12f).compositeOver(colors.surface)) {
        Row(
            Modifier.padding(start = 6.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            PinGlyph(colors.primary, glyphSize = 11.dp)
            Text(FeedbackCopy.PINNED, style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.2.sp), color = colors.onPrimaryContainer)
        }
    }
}

/** How the writer knows the owner ("Colleague"), on a neutral tint: a fact about the note, not a control. */
@Composable
private fun RelationBadge(label: String) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(6.dp), color = colors.onSurface.copy(alpha = 0.06f).compositeOver(colors.surface)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.2.sp),
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}
