package dev.yowkey.portfolio.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.yowkey.portfolio.app.owner.Loadable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** The widest a column of admin content grows on a tablet; past it the screen centres it. */
val ContentWidth = 720.dp

/**
 * A screen's data with its loading, empty and error states, under pull-to-refresh. With nothing loaded yet it
 * shows a spinner, or the failure and a Retry; with data it shows [content], and a failure to refresh comes as a
 * snackbar (the view model's notices) over what was there.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> LoadableContent(
    state: Loadable<T>,
    onRefresh: () -> Unit,
    isEmpty: (T) -> Boolean,
    emptyText: String,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.(T) -> Unit,
) {
    val value = state.value
    PullToRefreshBox(
        isRefreshing = state.loading && value != null,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize(),
    ) {
        when {
            value == null && state.error != null -> Message(state.error, action = "Retry", onAction = onRefresh)
            value == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            isEmpty(value) -> Message(emptyText)
            else -> content(value)
        }
    }
}

/** A centred sentence (and a button), scrollable so pull-to-refresh still works over it. */
@Composable
fun Message(text: String, action: String? = null, onAction: () -> Unit = {}) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Spacer(Modifier.height(48.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 420.dp),
        )
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = onAction) { Text(action) }
        }
    }
}

/** A small rounded label: a topic, a state, a kind. */
@Composable
fun Tag(text: String, container: Color = MaterialTheme.colorScheme.surfaceVariant, content: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = content,
        modifier = Modifier
            .background(container, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** A tag's colours for something that is fine and live (a note on the site, push on), on either theme. */
val Positive: Pair<Color, Color>
    @Composable get() = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) {
        Color(0xFF1F3A22) to Color(0xFFBFE5C2)
    } else {
        Color(0xFFDCEFD9) to Color(0xFF1B4D1F)
    }

/** Asks before something that cannot be undone. */
@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = { onDismiss(); onConfirm() }) { Text(confirm, color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Centres [content] at [ContentWidth] at most. */
@Composable
fun Centered(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = ContentWidth).fillMaxWidth()) { content() }
    }
}

/** "★★★★☆" for a rating out of five. */
fun stars(rating: Int): String = "★".repeat(rating.coerceIn(0, 5)) + "☆".repeat(5 - rating.coerceIn(0, 5))

private val TimeOnly = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
private val MonthDay = DateTimeFormatter.ofPattern("MMM d", Locale.US)
private val MonthDayYear = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)
private val WeekdayMonthDay = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US)

/** When something happened, in the owner's day: "Today 4:15 PM", "Yesterday 9:02 AM", "Oct 21, 4:15 PM". */
fun whenText(iso: String, zone: ZoneId, now: Instant = Instant.now()): String {
    val time = runCatching { Instant.parse(iso).atZone(zone) }.getOrNull() ?: return iso
    val today = now.atZone(zone).toLocalDate()
    val day = time.toLocalDate()
    return when (ChronoUnit.DAYS.between(day, today)) {
        0L -> "Today ${TimeOnly.format(time)}"
        1L -> "Yesterday ${TimeOnly.format(time)}"
        else -> if (day.year == today.year) "${MonthDay.format(time)}, ${TimeOnly.format(time)}" else MonthDayYear.format(time)
    }
}

/** A `YYYY-MM-DD` day as "Fri, Oct 23". */
fun dayText(day: String): String = runCatching { WeekdayMonthDay.format(LocalDate.parse(day)) }.getOrDefault(day)

/** A `YYYY-MM-DD` day as "Oct 23". */
fun shortDay(day: String): String = runCatching { MonthDay.format(LocalDate.parse(day)) }.getOrDefault(day)

/** "1 note", "3 notes". */
fun count(n: Int, one: String, many: String = one + "s"): String = "$n ${if (n == 1) one else many}"
