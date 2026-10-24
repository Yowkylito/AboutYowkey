package dev.yowkey.portfolio.ui.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import dev.yowkey.portfolio.model.GalleryItem
import dev.yowkey.portfolio.model.Award
import dev.yowkey.portfolio.model.Experience
import dev.yowkey.portfolio.model.Link
import dev.yowkey.portfolio.model.Service
import dev.yowkey.portfolio.model.SkillGroup
import dev.yowkey.portfolio.ui.image.LocalScreenshotImages
import dev.yowkey.portfolio.ui.image.ScreenshotImage
import dev.yowkey.portfolio.ui.components.Bullet
import dev.yowkey.portfolio.ui.components.LinkButton
import dev.yowkey.portfolio.ui.components.OutlinedCard
import dev.yowkey.portfolio.ui.components.PlayGlyph
import dev.yowkey.portfolio.ui.components.Tag

/** Lays out one row of an equal-height card grid; short rows keep their column width. */
@Composable
fun CardRow(
    columns: Int,
    count: Int,
    modifier: Modifier = Modifier,
    card: @Composable (index: Int, modifier: Modifier) -> Unit,
) {
    Row(modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        repeat(count) { card(it, Modifier.weight(1f).fillMaxHeight()) }
        repeat(columns - count) { Spacer(Modifier.weight(1f)) }
    }
}

@Composable
fun ServiceCard(service: Service, modifier: Modifier = Modifier) {
    OutlinedCard(modifier) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(service.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(service.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * An even grid of thumbnails across the card's width: up to five per row for phone screens, three for desktop or
 * mixed ones, so a clip plus four screenshots fills one row instead of leaving one thumbnail alone on the next.
 * A grid rather than a sideways scroller, because a mouse wheel cannot reach a hidden horizontal row. Each slot
 * takes its height from the image's aspect ratio, so the layout is final before any image loads. Clips show
 * their poster with a play mark.
 *
 * Plain weighted Rows, not FlowRow: FlowRow decides line breaks before applying weights, and an unbounded
 * aspectRatio claims the whole line, so every image wrapped onto its own row. Rows also answer the intrinsic
 * size queries CardRow makes for equal-height cards, which BoxWithConstraints cannot.
 *
 * @param firstIndex the gallery index of `items[0]`, so [onOpen] gets gallery indices (1 when a cover leads)
 */
@Composable
fun ScreenshotStrip(
    items: List<GalleryItem>,
    onOpen: (index: Int) -> Unit,
    modifier: Modifier = Modifier,
    firstIndex: Int = 0,
) {
    val loader = LocalScreenshotImages.current?.thumbnails
    val outline = MaterialTheme.colorScheme.outlineVariant
    val perRow = minOf(items.size, if (items.all { it.shot.aspectRatio < 1f }) 5 else 3).coerceAtLeast(1)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.chunked(perRow).forEachIndexed { row, chunk ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                chunk.forEachIndexed { column, item ->
                    val index = firstIndex + row * perRow + column
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(item.shot.aspectRatio)
                            .clip(RoundedCornerShape(10.dp))
                            .border(1.dp, outline, RoundedCornerShape(10.dp))
                            .clickable(onClickLabel = if (item.isClip) "Play clip" else "View larger", role = Role.Button) {
                                onOpen(index)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        ScreenshotImage(
                            path = item.cardPath,
                            contentDescription = item.shot.caption,
                            loader = loader,
                            modifier = Modifier.fillMaxSize(),
                        )
                        if (item.isClip) PlayGlyph()
                    }
                }
                repeat(perRow - chunk.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * A group inside a section, such as Projects' "Professional work": an accent label ruled to the edge, then its
 * [note]. Unlike both the section header above it and the card titles below, so the hierarchy reads section,
 * group, card. The label on the background is 4.8:1 in light and 8.4:1 in dark.
 */
@Composable
fun GroupHeader(title: String, note: String?, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                title.uppercase(),
                style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 1.4.sp),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { heading() },
            )
            HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
        }
        note?.let { SectionNote(it) }
    }
}

/** A short note under a header, such as why some projects have no source link. */
@Composable
fun SectionNote(text: String, modifier: Modifier = Modifier) {
    val style = MaterialTheme.typography.bodyMedium
    Text(text, style = style, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier.readingMeasure(style))
}

/**
 * A timeline entry; the rail is drawn, not composed, and stops at the last job. The role and its place read as one
 * heading block, the bullets start a clear step below it, and each bullet is a paragraph apart from the next. The dot
 * and the rail's start are placed in sp, as the title's line is, so the dot stays on the title at any text size.
 */
@Composable
fun JobEntry(experience: Experience, isLast: Boolean, wide: Boolean, modifier: Modifier = Modifier) {
    val rail = MaterialTheme.colorScheme.outline
    val dot = MaterialTheme.colorScheme.primary
    Row(modifier.fillMaxWidth()) {
        if (wide) {
            Text(
                experience.period,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(150.dp).padding(top = 2.dp, end = 16.dp),
            )
        }
        Column(
            Modifier
                .weight(1f)
                .drawBehind {
                    val x = 4.dp.toPx()
                    if (!isLast) drawLine(rail, Offset(x, 16.sp.toPx()), Offset(x, size.height), 1.dp.toPx())
                    drawCircle(dot, radius = 4.dp.toPx(), center = Offset(x, 9.sp.toPx()))
                }
                .padding(start = 28.dp, bottom = if (isLast) 0.dp else 44.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                "${experience.role} · ${experience.company}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            val meta = listOfNotNull(experience.period.takeUnless { wide }, experience.location).joinToString(" · ")
            if (meta.isNotEmpty()) {
                Text(meta, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (experience.highlights.isNotEmpty()) {
                Column(
                    Modifier.padding(top = 10.dp).readingMeasure(MaterialTheme.typography.bodyMedium),
                    verticalArrangement = Arrangement.spacedBy(ParagraphGap),
                ) {
                    experience.highlights.forEach { Bullet(it) }
                }
            }
        }
    }
}

@Composable
fun AwardList(awards: List<Award>, wide: Boolean, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(28.dp)) {
        awards.forEach { award ->
            Row {
                if (wide) {
                    Text(
                        award.date.orEmpty(),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(150.dp).padding(top = 2.dp, end = 16.dp),
                    )
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(award.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
                    if (!wide && award.date != null) {
                        Text(award.date, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    val detail = MaterialTheme.typography.bodyMedium
                    Text(
                        award.detail,
                        style = detail,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.readingMeasure(detail),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SkillGroups(groups: List<SkillGroup>, columns: Int, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(28.dp)) {
        groups.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                row.forEach { group ->
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(group.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            group.items.forEach { Tag(it) }
                        }
                    }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** The email and the profile's links; [onOpenLink] hears of each link once it has opened, to count LinkedIn and GitHub. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ContactBlock(email: String?, links: List<Link>, modifier: Modifier = Modifier, onOpenLink: (Link) -> Unit = {}) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text(
            "Hiring for Android, or have a project in mind? Let's talk.",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.readingMeasure(MaterialTheme.typography.headlineSmall),
        )
        email?.let {
            Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            email?.let { LinkButton(Link("Email me", "mailto:$it"), primary = true) }
            links.forEachIndexed { index, link ->
                LinkButton(link, primary = email == null && index == 0, onOpened = { onOpenLink(link) })
            }
        }
    }
}

/**
 * The page's last row. Back to top floats over the page's bottom end and nothing scrolls out from under it there, so
 * the words keep out of its corner: on a phone they wrap short of it instead of running under it.
 */
@Composable
fun Footer(platformLabel: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Box(Modifier.padding(end = BackToTopRoom, bottom = 8.dp)) {
            Text(
                "Built with Compose Multiplatform · running on $platformLabel",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Back to top's 48 dp and a gap: the corner the footer's words keep out of. */
private val BackToTopRoom = 56.dp
