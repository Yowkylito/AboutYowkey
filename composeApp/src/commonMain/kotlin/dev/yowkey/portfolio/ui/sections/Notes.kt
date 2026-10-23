package dev.yowkey.portfolio.ui.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.yowkey.portfolio.model.FeedbackCopy
import dev.yowkey.portfolio.model.PublicNote
import dev.yowkey.portfolio.ui.components.OutlinedCard
import dev.yowkey.portfolio.ui.components.StarRow

/**
 * One row of the notes visitors left and the owner published, as cards above the form in the Contact section: the
 * stars, the words, who and when. Only the first row carries the title.
 */
@Composable
fun NoteCards(notes: List<PublicNote>, columns: Int, showsTitle: Boolean, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (showsTitle) {
            Text(
                FeedbackCopy.NOTES_TITLE,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.semantics { heading() },
            )
        }
        CardRow(columns, notes.size) { index, cardModifier -> NoteCard(notes[index], cardModifier) }
    }
}

@Composable
private fun NoteCard(note: PublicNote, modifier: Modifier = Modifier) {
    OutlinedCard(modifier) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            note.rating?.let { StarRow(it) }
            Text(note.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(note.byLine, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
