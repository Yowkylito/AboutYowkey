package dev.yowkey.portfolio.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.yowkey.portfolio.admin.CountryCount
import dev.yowkey.portfolio.admin.DayVisits
import dev.yowkey.portfolio.admin.MessageFilter
import dev.yowkey.portfolio.admin.PushStatus
import dev.yowkey.portfolio.admin.flagOf
import dev.yowkey.portfolio.admin.topCountries
import dev.yowkey.portfolio.admin.visitSeries
import dev.yowkey.portfolio.app.owner.Dashboard
import dev.yowkey.portfolio.app.owner.OwnerViewModel
import dev.yowkey.portfolio.app.owner.Tab

/** Today at a glance: the figures, the last 30 days of visits, where they came from, and whether push works. */
@Composable
fun DashboardScreen(owner: OwnerViewModel) {
    val state by owner.dashboard.collectAsState()
    LoadableContent(
        state = state,
        onRefresh = { owner.refresh(Tab.Dashboard) },
        isEmpty = { false },
        emptyText = "",
    ) { dashboard ->
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Centered {
                DashboardBody(
                    dashboard,
                    onMessages = { filter -> owner.setFilter(filter); owner.select(Tab.Messages) },
                    onNotes = { owner.select(Tab.Notes) },
                )
            }
        }
    }
}

@Composable
private fun DashboardBody(dashboard: Dashboard, onMessages: (MessageFilter) -> Unit, onNotes: () -> Unit) {
    val summary = dashboard.summary
    val linkedIn = summary.clicksOn("linkedin")
    val gitHub = summary.clicksOn("github")
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            "Today · ${dayText(summary.day)}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columns = if (maxWidth >= 560.dp) 3 else 2
            val tiles = listOf<@Composable (Modifier) -> Unit>(
                { Tile("Visits today", summary.visits.today, "${summary.visits.total} in all" + (summary.visits.since?.let { " since ${shortDay(it)}" } ?: ""), it) },
                { Tile("LinkedIn clicks", linkedIn.today, "${linkedIn.total} in all", it) },
                { Tile("Unread messages", summary.messages.unread, "${summary.messages.total} in all", it, highlight = summary.messages.unread > 0, onClick = { onMessages(MessageFilter.Unread) }) },
                { Tile("Want to hire you", summary.messages.hiring, "job or freelance", it, highlight = summary.messages.hiring > 0, onClick = { onMessages(MessageFilter.Hiring) }) },
                { Tile("GitHub clicks", gitHub.today, "${gitHub.total} in all", it) },
                { Tile("Notes today", summary.notes.today, "${summary.notes.published} of ${summary.notes.total} on the site", it, onClick = onNotes) },
            )
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                tiles.chunked(columns).forEach { row ->
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { tile -> tile(Modifier.weight(1f).fillMaxHeight()) }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        VisitsCard(visitSeries(dashboard.days, summary.day, 30))
        CountriesCard(topCountries(dashboard.days, 5))
        PushCard(summary.push)
    }
}

@Composable
private fun Tile(
    label: String,
    value: Int,
    detail: String,
    modifier: Modifier,
    highlight: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val colors = if (highlight) {
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer)
    } else {
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    }
    val body: @Composable () -> Unit = {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(value.toString(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = if (highlight) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (onClick != null) Card(onClick = onClick, modifier = modifier, colors = colors) { body() } else Card(modifier = modifier, colors = colors) { body() }
}

/** Thirty bars, oldest on the left, today's in the accent; the rest in a tint of it. */
@Composable
private fun VisitsCard(series: List<DayVisits>) {
    val total = series.sumOf { it.visits }
    val best = series.maxByOrNull { it.visits }
    val today = series.lastOrNull()
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Visits, last 30 days", style = MaterialTheme.typography.titleMedium)
            Text(
                buildString {
                    append(count(total, "visit")).append(" in 30 days")
                    if (best != null && best.visits > 0) append(" · most ${best.visits} on ${shortDay(best.day)}")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val accent = MaterialTheme.colorScheme.primary
            val tint = accent.copy(alpha = 0.3f)
            val empty = MaterialTheme.colorScheme.outlineVariant
            val max = (best?.visits ?: 0).coerceAtLeast(1)
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .semantics {
                        contentDescription = "Visits over the last 30 days: $total in all, ${today?.visits ?: 0} today" +
                            (best?.takeIf { it.visits > 0 }?.let { ", most ${it.visits} on ${shortDay(it.day)}" } ?: "")
                    },
            ) {
                if (series.isEmpty()) return@Canvas
                val gap = 3.dp.toPx()
                val barWidth = (size.width - gap * (series.size - 1)) / series.size
                val radius = CornerRadius(barWidth / 3, barWidth / 3)
                series.forEachIndexed { index, day ->
                    val height = if (day.visits == 0) 2.dp.toPx() else (size.height * day.visits / max).coerceAtLeast(3.dp.toPx())
                    val color = when {
                        day.isToday -> accent
                        day.visits == 0 -> empty
                        else -> tint
                    }
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(index * (barWidth + gap), size.height - height),
                        size = Size(barWidth, height),
                        cornerRadius = radius,
                    )
                }
            }
            Row(Modifier.fillMaxWidth()) {
                Text(series.firstOrNull()?.let { shortDay(it.day) }.orEmpty(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                Text("Today · ${today?.visits ?: 0}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun CountriesCard(countries: List<CountryCount>) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Top countries, last 30 days", style = MaterialTheme.typography.titleMedium)
            if (countries.isEmpty()) {
                Text("No countries yet: they are counted from the first visit after this update.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val most = countries.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1
            val style = MaterialTheme.typography.bodyMedium
            val labels = countries.map { "${flagOf(it.country)} ${it.country}".trim() }
            // Each column is as wide as its widest entry, measured: fixed at 64 and 40 dp, a four-digit count broke
            // over two lines at a 1.3 font scale and a five-digit one at the default. The bars still line up.
            val measurer = rememberTextMeasurer()
            val labelWidth = (widest(measurer, labels, style) + 8.dp).coerceAtLeast(64.dp)
            val countWidth = widest(measurer, countries.map { it.count.toString() }, style).coerceAtLeast(40.dp)
            countries.forEachIndexed { index, country ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        labels[index],
                        style = style,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.width(labelWidth),
                    )
                    Box(Modifier.weight(1f).height(8.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))) {
                        Box(
                            Modifier
                                .fillMaxWidth(country.count.toFloat() / most)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp)),
                        )
                    }
                    Text(
                        country.count.toString(),
                        style = style,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(start = 12.dp).width(countWidth),
                    )
                }
            }
        }
    }
}

/** The width of the widest of [texts] on one line in [style], at the current density and font scale. */
@Composable
private fun widest(measurer: TextMeasurer, texts: List<String>, style: TextStyle): Dp {
    val px = texts.maxOfOrNull { measurer.measure(it, style, maxLines = 1, softWrap = false).size.width } ?: 0
    return with(LocalDensity.current) { px.toDp() }
}

@Composable
private fun PushCard(push: PushStatus) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .padding(end = 12.dp)
                    .width(10.dp)
                    .height(10.dp)
                    .background(if (push.configured && push.devices > 0) Color(0xFF2E7D32) else MaterialTheme.colorScheme.outline, RoundedCornerShape(5.dp)),
            )
            Column(Modifier.weight(1f)) {
                Text("Push", style = MaterialTheme.typography.titleMedium)
                Text(
                    when {
                        !push.configured -> "Not set up on the server yet (no FCM_SERVICE_ACCOUNT). Alerts still come by the 15-minute check."
                        push.devices == 0 -> "Set up on the server, but no phone is registered."
                        else -> "On: the server pushes to ${count(push.devices, "phone")}."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
