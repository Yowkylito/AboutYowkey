package dev.yowkey.portfolio.ui.sections

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.yowkey.portfolio.model.FeedbackCopy
import dev.yowkey.portfolio.model.RatingSummary
import dev.yowkey.portfolio.model.Relation
import dev.yowkey.portfolio.ui.StarSheet
import dev.yowkey.portfolio.ui.components.CloseGlyph
import dev.yowkey.portfolio.ui.components.StarRow
import dev.yowkey.portfolio.ui.theme.LocalReducedMotion
import kotlinx.coroutines.launch

/**
 * The sheet a dashboard bar opens: [sheet]'s star, how many rated it and how many of those left words to read
 * ([ratings], the whole site's), the sorter by how the writers know the owner, then the notes, one column, pinned
 * first. "Show more" sits at the end of the list while more are left, and for All, a line for the ratings whose
 * words are private. It closes on the close button, the scrim, a drag down or Back (Escape on the web), and is
 * [ModalBottomSheet]'s own width on a wide window. The list fades back while a new pick loads, and the close button
 * lowers the sheet as a drag would; both at once under reduced motion.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StarNotesSheet(
    sheet: StarSheet,
    ratings: RatingSummary?,
    onPick: (Relation?) -> Unit,
    onMore: () -> Unit,
    onClose: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val reducedMotion = LocalReducedMotion.current
    val colors = MaterialTheme.colorScheme
    // The scrim, a drag and Back lower the sheet themselves before they dismiss it; the button does the same.
    val close: () -> Unit = {
        if (reducedMotion) onClose()
        else scope.launch { sheetState.hide() }.invokeOnCompletion { if (!sheetState.isVisible) onClose() }
    }
    val title = FeedbackCopy.sheetTitle(sheet.stars)
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheetState,
        // The page's own background, so the note cards sit on it as they do under the dashboard.
        containerColor = colors.background,
        contentColor = colors.onBackground,
        tonalElevation = 0.dp,
        // Drawn in the content instead, under the header's light: in its own slot it sat on a band of its own.
        dragHandle = null,
    ) {
        val glow = colors.primary.copy(alpha = 0.07f)
        Column(
            Modifier
                .fillMaxWidth()
                .semantics { paneTitle = title }
                .drawBehind {
                    // The dashboard card's light, from the top left, over the header.
                    val end = Offset(size.width * 0.7f, minOf(size.height, 280.dp.toPx()))
                    drawRect(Brush.linearGradient(listOf(glow, Color.Transparent), start = Offset.Zero, end = end))
                },
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { BottomSheetDefaults.DragHandle() }
            SheetHeader(sheet, title, ratings, close)
            RelationSorter(sheet, onPick)
            HorizontalDivider(color = colors.outlineVariant)
            SheetList(sheet, ratings?.privateCountOf(sheet.stars) ?: 0, onPick, onMore)
        }
    }
}

/** The star drawn, "5-star notes" as the heading, "4 ratings · 1 note to read", and the close button. */
@Composable
private fun SheetHeader(sheet: StarSheet, title: String, ratings: RatingSummary?, onClose: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f).padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // The title says it: the stars are only drawn.
            StarRow(sheet.stars, Modifier.clearAndSetSemantics {}, glyphSize = 18.dp)
            Text(title, style = MaterialTheme.typography.titleLarge, color = colors.onBackground, modifier = Modifier.semantics { heading() })
            Row(Modifier.heightIn(min = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (sheet.page != null) {
                    Text(
                        FeedbackCopy.sheetSummary(ratings?.countOf(sheet.stars) ?: 0, sheet.readable),
                        style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                        color = colors.onSurfaceVariant,
                    )
                }
                if (sheet.page != null && sheet.loading) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
            }
        }
        IconButton(onClick = onClose, modifier = Modifier.semantics { contentDescription = FeedbackCopy.CLOSE }) {
            CloseGlyph(colors.onSurfaceVariant)
        }
    }
}

/**
 * All · Colleagues · Friends · Special someone · Others, each with its count: one choice at a time, scrolling
 * sideways when the window is narrower than the five. A radio group, so a screen reader hears "Friends, 2 notes,
 * selected". A relation without notes cannot be picked (All always can); until the first page answers, only All.
 * The picked chip takes the dashboard's tint of the accent.
 */
@Composable
private fun RelationSorter(sheet: StarSheet, onPick: (Relation?) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val choices = remember { listOf<Relation?>(null) + Relation.offered }
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .selectableGroup()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        choices.forEach { relation ->
            val selected = relation == sheet.relation
            val count = sheet.countOf(relation)
            val known = sheet.page != null
            FilterChip(
                selected = selected,
                onClick = { onPick(relation) },
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(relation?.plural ?: FeedbackCopy.ALL_RELATIONS)
                        if (known) {
                            Text(
                                "$count",
                                style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 0.sp, fontFeatureSettings = "tnum"),
                                color = if (selected) colors.primary else colors.onSurfaceVariant,
                                modifier = Modifier.semantics { contentDescription = "$count ${if (count == 1) "note" else "notes"}" },
                            )
                        }
                    }
                },
                enabled = relation == null || selected || (known && count > 0),
                shape = RoundedCornerShape(50),
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = colors.surface,
                    labelColor = colors.onSurface,
                    selectedContainerColor = colors.primary.copy(alpha = 0.14f).compositeOver(colors.background),
                    selectedLabelColor = colors.onSurface,
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = relation == null || selected || (known && count > 0),
                    selected = selected,
                    borderColor = colors.outlineVariant,
                    selectedBorderColor = colors.primary.copy(alpha = 0.45f),
                    selectedBorderWidth = 1.dp,
                ),
                // Outermost, so it wins over the chip's own checkbox role.
                modifier = Modifier.semantics { role = Role.RadioButton },
            )
        }
    }
}

/**
 * The pick's notes, then "Show more" while more are left and, for All, the private ratings' line; a spinner until
 * the first page answers, a sentence when there is nothing to read or it could not be loaded. Back to the top for
 * each new pick, its cards fading in.
 */
@Composable
private fun SheetList(sheet: StarSheet, privateCount: Int, onPick: (Relation?) -> Unit, onMore: () -> Unit) {
    val reducedMotion = LocalReducedMotion.current
    val listState = rememberLazyListState()
    LaunchedEffect(sheet.relation) { listState.scrollToItem(0) }
    val alpha by animateFloatAsState(
        if (sheet.loading && sheet.page != null) 0.45f else 1f,
        if (reducedMotion) snap() else tween(180),
        label = "sheet-list-alpha",
    )
    val page = sheet.page
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxWidth().heightIn(min = 240.dp).graphicsLayer { this.alpha = alpha },
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when {
            page == null && sheet.loading -> item(key = "loading") {
                Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.5.dp)
                }
            }
            page == null -> item(key = "failed") {
                Row(
                    Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SheetLine(FeedbackCopy.SHEET_FAILED, Modifier.weight(1f, fill = false))
                    TextButton(onClick = { onPick(sheet.relation) }) { Text(FeedbackCopy.TRY_AGAIN) }
                }
            }
            else -> {
                items(page.notes, key = { it.id }) { note ->
                    NoteCard(
                        note,
                        Modifier.fillMaxWidth().animateItem(
                            fadeInSpec = if (reducedMotion) null else tween(220),
                            placementSpec = if (reducedMotion) null else tween(220),
                            fadeOutSpec = null,
                        ),
                    )
                }
                if (page.notes.isEmpty() && !sheet.loading) {
                    item(key = "none") {
                        SheetLine(FeedbackCopy.noneInSheet(sheet.relation, sheet.stars), Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    }
                }
                if (page.more) item(key = "more") { MoreNotesButton(sheet.loadingMore, onMore, Modifier.padding(top = 4.dp)) }
            }
        }
        if (sheet.relation == null && privateCount > 0 && page != null) {
            item(key = "private") { PrivateLine(FeedbackCopy.privateInSheet(privateCount, sheet.stars)) }
        }
    }
}

@Composable
private fun SheetLine(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier.padding(vertical = 8.dp))
}

/** The private ratings, counted but not shown: a quiet note at the end of the list, not one more card. */
@Composable
private fun PrivateLine(text: String) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = colors.onSurface.copy(alpha = 0.04f).compositeOver(colors.background),
        border = BorderStroke(1.dp, colors.outlineVariant),
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}
