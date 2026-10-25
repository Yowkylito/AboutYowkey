package dev.yowkey.portfolio.ui.sections

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.yowkey.portfolio.model.FeedbackCopy
import dev.yowkey.portfolio.model.LiveStats
import dev.yowkey.portfolio.model.RatingSummary
import dev.yowkey.portfolio.model.SiteNotes
import dev.yowkey.portfolio.model.oneDecimal
import dev.yowkey.portfolio.ui.components.StarGlyph
import dev.yowkey.portfolio.ui.components.StarMeter
import dev.yowkey.portfolio.ui.theme.LocalReducedMotion
import kotlin.math.max

/**
 * The notes' dashboard, above them in the Contact section: the average to one decimal with its stars drawn to the
 * fraction, how many rated, and a bar for each number of stars with its count and share. Each bar also opens that
 * star's sheet ([onOpenSheet]), its notes sorted by how their writers know the owner. Under it, why there are fewer
 * notes than ratings when some were sent privately, or that there are none yet ([loading]: not while the list is
 * on its way again).
 */
@Composable
fun RatingsDashboard(
    notes: SiteNotes,
    loading: Boolean,
    onOpenSheet: (Int) -> Unit,
    wide: Boolean,
    modifier: Modifier = Modifier,
) {
    val ratings = notes.ratings
    val average = ratings.average
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            FeedbackCopy.NOTES_TITLE,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { heading() },
        )
        if (ratings.count >= LiveStats.MIN_RATINGS && average != null) {
            DashboardCard(wide) {
                if (wide) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Average(average, ratings.count, Modifier.weight(0.36f), centered = true)
                        VerticalDivider(Modifier.height(168.dp).padding(horizontal = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        StarBars(ratings, onOpenSheet, Modifier.weight(0.64f).padding(start = 16.dp))
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        Average(average, ratings.count, Modifier.fillMaxWidth(), centered = false)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        StarBars(ratings, onOpenSheet, Modifier.fillMaxWidth())
                    }
                }
            }
            if (ratings.keptPrivate > 0) {
                Text(
                    FeedbackCopy.keptPrivate(ratings.keptPrivate),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (notes.notes.isEmpty() && ratings.count == 0 && !loading) {
            Text(
                FeedbackCopy.NO_NOTES,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

/** The card the dashboard sits on: the page's outlined surface, lit faintly from its top left in the accent. */
@Composable
private fun DashboardCard(wide: Boolean, content: @Composable () -> Unit) {
    val glow = MaterialTheme.colorScheme.primary.copy(alpha = 0.07f)
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            Modifier
                .drawBehind {
                    drawRect(Brush.linearGradient(listOf(glow, Color.Transparent), start = Offset.Zero, end = Offset(size.width * 0.7f, size.height)))
                }
                .padding(if (wide) 32.dp else 20.dp),
        ) { content() }
    }
}

/** "4.8 / 5", its stars, and "from 12 ratings", read as one line. */
@Composable
private fun Average(average: Double, count: Int, modifier: Modifier, centered: Boolean) {
    val spoken = "Average ${oneDecimal(average)} of 5, ${FeedbackCopy.ratingsCount(count)}"
    Column(
        modifier.clearAndSetSemantics { contentDescription = spoken },
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                oneDecimal(average),
                style = MaterialTheme.typography.displayMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-1.5).sp,
                    fontFeatureSettings = "tnum",
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                " / 5",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 9.dp),
            )
        }
        StarMeter(average, glyphSize = 22.dp)
        Text(FeedbackCopy.ratingsCount(count), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Five down to one star, each a bar and the way into its sheet. */
@Composable
private fun StarBars(ratings: RatingSummary, onOpenSheet: (Int) -> Unit, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        (5 downTo 1).forEachIndexed { index, stars ->
            StarBar(
                stars = stars,
                count = ratings.countOf(stars),
                share = ratings.shareOf(stars),
                percent = ratings.percentOf(stars),
                delayMillis = 0,
                onClick = { onOpenSheet(stars) },
            )
        }
    }
}

/**
 * One bar: "5 ★", the share drawn as a rounded fill over its track, the count and the percentage. The fill grows in
 * from the left the first time the row is shown, one bar after the other (at once with reduced motion), and only
 * the drawing is redone while it grows. A button, read as its numbers ("5 stars: 18 ratings, 75 percent") with its
 * action, "Show the 5-star notes".
 */
@Composable
private fun StarBar(
    stars: Int,
    count: Int,
    share: Float,
    percent: String,
    delayMillis: Int,
    onClick: () -> Unit,
) {
    val reducedMotion = LocalReducedMotion.current
    val fill = remember { Animatable(if (reducedMotion) share else 0f) }
    LaunchedEffect(share, reducedMotion) {
        if (reducedMotion) fill.snapTo(share)
        else fill.animateTo(share, tween(durationMillis = 400, delayMillis = delayMillis, easing = FastOutSlowInEasing))
    }
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val track = colors.surfaceContainerHighest
    val accent = colors.primary
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (hovered) colors.onSurface.copy(alpha = 0.05f) else Color.Transparent)
            .clickable(
                interactionSource = interaction,
                indication = ripple(),
                role = Role.Button,
                onClickLabel = FeedbackCopy.openSheet(stars),
                onClick = onClick,
            )
            .semantics { contentDescription = FeedbackCopy.barLabel(stars, count, percent) }
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            Modifier.width(30.dp).clearAndSetSemantics {},
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("$stars", style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 0.sp, fontFeatureSettings = "tnum"), color = colors.onSurface)
            StarGlyph(filled = true, color = accent, glyphSize = 12.dp)
        }
        Spacer(
            Modifier
                .weight(1f)
                .height(10.dp)
                .clearAndSetSemantics {}
                .drawBehind {
                    val round = CornerRadius(size.height / 2)
                    drawRoundRect(track, cornerRadius = round)
                    val width = size.width * fill.value
                    if (width > 0f) {
                        // Never thinner than it is tall, so one rating in a hundred still shows as a dot.
                        val drawn = max(width, size.height)
                        drawRoundRect(
                            Brush.horizontalGradient(listOf(accent.copy(alpha = 0.72f), accent), endX = drawn),
                            size = Size(drawn, size.height),
                            cornerRadius = round,
                        )
                    }
                },
        )
        Text(
            "$count",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp, fontFeatureSettings = "tnum"),
            color = colors.onSurface,
            textAlign = TextAlign.End,
            modifier = Modifier.width(36.dp).clearAndSetSemantics {},
        )
        Text(
            percent,
            style = MaterialTheme.typography.bodySmall.copy(letterSpacing = 0.sp, fontFeatureSettings = "tnum"),
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.width(40.dp).clearAndSetSemantics {},
        )
    }
}

/** Under the notes while more are left: one button for the next page, waiting while it comes. */
@Composable
fun MoreNotesButton(loading: Boolean, onMore: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        OutlinedButton(onClick = onMore, enabled = !loading) {
            Text(if (loading) FeedbackCopy.LOADING_MORE else FeedbackCopy.MORE)
        }
    }
}
