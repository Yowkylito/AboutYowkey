package dev.yowkey.portfolio.ui.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.yowkey.portfolio.model.PublicNote
import dev.yowkey.portfolio.ui.components.OutlinedCard
import dev.yowkey.portfolio.ui.components.StarRow

/**
 * One row of the public notes, as cards under the ratings dashboard in the Contact section (which carries the
 * title): the stars, the words, who and when.
 */
@Composable
fun NoteCards(notes: List<PublicNote>, columns: Int, modifier: Modifier = Modifier) {
    CardRow(columns, notes.size, modifier) { index, cardModifier -> NoteCard(notes[index], cardModifier) }
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
