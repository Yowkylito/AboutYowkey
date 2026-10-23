package dev.yowkey.portfolio.ui.detail

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.yowkey.portfolio.model.Decision
import dev.yowkey.portfolio.model.Engineering
import dev.yowkey.portfolio.model.GalleryItem
import dev.yowkey.portfolio.model.Link
import dev.yowkey.portfolio.model.Module
import dev.yowkey.portfolio.model.Project
import dev.yowkey.portfolio.model.ProofPoint
import dev.yowkey.portfolio.model.RepoVisibility
import dev.yowkey.portfolio.model.Screenshot
import dev.yowkey.portfolio.ui.PageLayout
import dev.yowkey.portfolio.ui.components.BackGlyph
import dev.yowkey.portfolio.ui.components.Bullet
import dev.yowkey.portfolio.ui.components.CheckGlyph
import dev.yowkey.portfolio.ui.components.LinkButton
import dev.yowkey.portfolio.ui.components.PlatformChip
import dev.yowkey.portfolio.ui.components.PlayPauseButton
import dev.yowkey.portfolio.ui.components.Tag
import dev.yowkey.portfolio.ui.components.TechChip
import dev.yowkey.portfolio.ui.components.ThemeToggle
import dev.yowkey.portfolio.ui.image.CoverClip
import dev.yowkey.portfolio.ui.image.ScreenshotViewer
import dev.yowkey.portfolio.ui.image.ViewerMediaImage
import dev.yowkey.portfolio.ui.image.ViewerRequest
import dev.yowkey.portfolio.ui.sections.FigureList
import dev.yowkey.portfolio.ui.sections.MediaFrame
import dev.yowkey.portfolio.ui.sections.mediaFrameCorner
import dev.yowkey.portfolio.ui.sections.ProjectHeading
import dev.yowkey.portfolio.ui.sections.ScreenshotStrip
import dev.yowkey.portfolio.ui.theme.LocalReducedMotion
import dev.yowkey.portfolio.ui.theme.PortfolioTheme

/**
 * One project, in the order a recruiter and then an engineering manager read it: what it is (media, title, line),
 * what it did (highlights), how it is built (chips, architecture, module diagram, decisions, quality), then the
 * gallery, the full stack and the links. Work has no media; after its title come its figures, impact, my
 * contributions and the optimizations and audits, then how it is built.
 *
 * Expanded windows put the media on the left and the text on the right, the media staying in view while the text
 * scrolls; narrower ones stack everything in one column. Back is the top bar's button, Escape, the button at the end, or the browser's Back (via the router).
 * The cover clip plays while the screen shows, except under reduced motion and while the viewer is open (the
 * viewer plays its own copy).
 */
@Composable
fun ProjectDetailScreen(
    project: Project,
    layout: PageLayout,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    val focus = remember { FocusRequester() }
    val reducedMotion = LocalReducedMotion.current
    var viewer by remember(project) { mutableStateOf<ViewerRequest?>(null) }
    val gallery = remember(project) { project.gallery }
    val lead = remember(project) { project.leadMedia }
    val strip = remember(gallery, lead) { gallery.filter { it != lead && !it.isCoverMedia } }
    val openViewer: (Int) -> Unit = remember(project) { { index -> viewer = ViewerRequest(project.title, project.gallery, index) } }
    val expanded = layout.wideSections
    val twoColumns = expanded && lead != null

    viewer?.let { ScreenshotViewer(it, onDismiss = { viewer = null }) }

    Column(
        modifier
            .fillMaxSize()
            .focusRequester(focus)
            .focusable()
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) onBack().let { true } else false
            },
    ) {
        DetailTopBar(project.title, onBack, layout.gutter, scrolled = { scroll.value > 0 })
        Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll), contentAlignment = Alignment.TopCenter) {
            val content = Modifier
                .widthIn(max = if (twoColumns || lead != null) PageLayout.MaxWidth else TextColumnMaxWidth)
                .fillMaxWidth()
                .padding(horizontal = layout.gutter)
                .padding(top = if (layout.compact) 20.dp else 40.dp, bottom = 96.dp)
            val leadIndex = lead?.let { gallery.indexOf(it) } ?: 0
            // Under reduced motion the hero clip waits for the reader's Play instead of starting on its own.
            var chosenToPlay by remember(project) { mutableStateOf(false) }
            val playing = (!reducedMotion || chosenToPlay) && viewer == null
            val togglePlay: (() -> Unit)? = if (reducedMotion && lead?.isClip == true) ({ chosenToPlay = !chosenToPlay }) else null
            if (twoColumns) {
                // The media column stays in view while the longer text column scrolls past it, until both end
                // together. The offset is read in the layer, so scrolling moves it without recomposing anything.
                var rowHeight by remember { mutableIntStateOf(0) }
                var mediaHeight by remember { mutableIntStateOf(0) }
                Row(content.onSizeChanged { rowHeight = it.height }) {
                    Column(
                        Modifier
                            .weight(0.92f)
                            .onSizeChanged { mediaHeight = it.height }
                            .graphicsLayer {
                                translationY = scroll.value.toFloat().coerceIn(0f, (rowHeight - mediaHeight).coerceAtLeast(0).toFloat())
                            },
                        verticalArrangement = Arrangement.spacedBy(28.dp),
                    ) {
                        DetailMedia(lead!!, playing, compact = false, onOpen = { openViewer(leadIndex) }, onTogglePlay = togglePlay)
                        GallerySection(strip, gallery.indexOf(strip.firstOrNull()), openViewer)
                    }
                    Spacer(Modifier.width(48.dp))
                    Column(Modifier.weight(1.08f), verticalArrangement = Arrangement.spacedBy(SectionGap)) {
                        DetailText(project, compact = false, onBack = onBack)
                    }
                }
            } else {
                Column(content, verticalArrangement = Arrangement.spacedBy(SectionGap)) {
                    lead?.let { DetailMedia(it, playing, compact = layout.compact, onOpen = { openViewer(leadIndex) }, onTogglePlay = togglePlay) }
                    DetailText(project, compact = layout.compact, onBack = onBack, gallery = {
                        GallerySection(strip, gallery.indexOf(strip.firstOrNull()), openViewer)
                    })
                }
            }
        }
    }
    LaunchedEffect(project) { focus.requestFocus() }
}

/**
 * The text column. [gallery] is slotted between "How it's built" and the stack in the one-column layout; the
 * two-column layout shows the gallery under the media instead and passes nothing.
 */
@Composable
private fun DetailText(project: Project, compact: Boolean, onBack: () -> Unit, gallery: (@Composable () -> Unit)? = null) {
    DetailHeader(project, compact)
    // Work, outcome first: the numbers, what it changed, my part in it, then the optimizations and audits.
    if (project.figures.isNotEmpty()) FigureList(project.figures, large = !compact)
    if (project.impact.isNotEmpty()) {
        DetailSection("Impact") { ImpactList(project.impact) }
    }
    if (project.highlights.isNotEmpty()) {
        DetailSection(project.highlightsTitle) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { project.highlights.forEach { Bullet(it) } }
        }
    }
    if (project.optimizations.isNotEmpty()) {
        DetailSection("Optimizations and audits") { Checklist(project.optimizations) }
    }
    project.engineering?.let { engineering -> HowItsBuilt(engineering) }
    gallery?.invoke()
    if (project.stack.isNotEmpty()) {
        DetailSection("Stack") { Tags(project.stack) }
    }
    if (project.links.isNotEmpty()) {
        DetailSection("Links") { Links(project.links) }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    OutlinedButton(onClick = onBack) { Text("Back to all projects") }
}

@Composable
private fun DetailHeader(project: Project, compact: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ProjectHeading(
            project,
            if (compact) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.displayMedium,
            iconSize = if (compact) 48.dp else 64.dp,
        )
        project.tagline?.let {
            Text(it, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
        }
        Text(project.summary, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        project.disclaimer?.let { Disclaimer(it) }
    }
}

/** An unofficial concept's note, boxed in the badge's outline so it reads as a note on the page, not part of the story. */
@Composable
private fun Disclaimer(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

/** Chips, the architecture paragraph, the module diagram, the decisions and the quality checklist, each if given. */
@Composable
private fun HowItsBuilt(engineering: Engineering) {
    DetailSection("How it's built") {
        Column(verticalArrangement = Arrangement.spacedBy(28.dp)) {
            if (engineering.builtWith.isNotEmpty() || engineering.platforms.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (engineering.builtWith.isNotEmpty()) LabelledChips("Built with") { engineering.builtWith.forEach { TechChip(it) } }
                    if (engineering.platforms.isNotEmpty()) LabelledChips("Runs on") { engineering.platforms.forEach { PlatformChip(it) } }
                }
            }
            engineering.architecture?.let {
                Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            }
            if (engineering.modules.isNotEmpty()) {
                SubSection("Modules") { ModuleDiagram(engineering.modules) }
            }
            if (engineering.decisions.isNotEmpty()) {
                SubSection("Key decisions") { Decisions(engineering.decisions) }
            }
            if (engineering.quality.isNotEmpty()) {
                SubSection("Quality") { Checklist(engineering.quality) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LabelledChips(label: String, chips: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { chips() }
    }
}

/** Numbered rows: the decision as a title, its reason under it, a hairline between rows. */
@Composable
private fun Decisions(decisions: List<Decision>) {
    Column {
        decisions.forEachIndexed { index, decision ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                Modifier.padding(vertical = 14.dp).semantics(mergeDescendants = true) {},
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    (index + 1).toString().padStart(2, '0'),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 3.dp),
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(decision.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text(decision.detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** What the work changed, a step up from the bullets that follow: larger text beside one accent rule. */
@Composable
private fun ImpactList(items: List<String>) {
    val rule = MaterialTheme.colorScheme.primary
    Column(
        Modifier
            .drawBehind {
                val x = 1.5.dp.toPx()
                drawLine(rule, Offset(x, 0f), Offset(x, size.height), 3.dp.toPx())
            }
            .padding(start = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items.forEach { Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface) }
    }
}

@Composable
private fun Checklist(items: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items.forEach { item ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                CheckGlyph(MaterialTheme.colorScheme.primary, Modifier.padding(top = 2.dp))
                Text(item, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun GallerySection(items: List<GalleryItem>, firstIndex: Int, onOpen: (Int) -> Unit) {
    if (items.isEmpty()) return
    DetailSection("Screens and clips") { ScreenshotStrip(items, onOpen, firstIndex = firstIndex) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Tags(items: List<String>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { Tag(it) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Links(links: List<Link>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        links.forEachIndexed { index, link -> LinkButton(link, primary = index == 0) }
    }
}

@Composable
private fun DetailSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { heading() },
        )
        content()
    }
}

@Composable
private fun SubSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { heading() },
        )
        content()
    }
}

/**
 * The lead media, large, on the same accent wash as the card's stage: the cover clip playing (its poster first),
 * or the full-size still. Tapping it opens the viewer there.
 */
@Composable
private fun DetailMedia(
    item: GalleryItem,
    playing: Boolean,
    compact: Boolean,
    onOpen: () -> Unit,
    onTogglePlay: (() -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val wash = scheme.primary.copy(alpha = 0.10f).compositeOver(scheme.surfaceVariant)
    val base = scheme.surfaceVariant
    val glow = scheme.primary.copy(alpha = 0.16f)
    val portrait = item.shot.aspectRatio < 1f
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .drawWithCache {
                val gradient = Brush.verticalGradient(listOf(wash, base))
                val halo = Brush.radialGradient(
                    listOf(glow, glow.copy(alpha = 0f)),
                    center = Offset(size.width / 2, size.height * 0.35f),
                    radius = size.maxDimension * 0.6f,
                )
                onDrawBehind {
                    drawRect(gradient)
                    drawRect(halo)
                }
            }
            .clickable(onClickLabel = if (item.isClip) "Open the clip" else "View larger", role = Role.Button, onClick = onOpen)
            .padding(if (portrait) 28.dp else if (compact) 14.dp else 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        val box = if (portrait) Modifier.height(if (compact) PortraitHeroCompact else PortraitHero) else Modifier.fillMaxWidth()
        MediaFrame(item.shot, box) { frame ->
            if (item.isClip) {
                // Under reduced motion the Play/Pause button sits over the clip, and a video would hide it: keep the WebP.
                CoverClip(
                    item.shot,
                    playing,
                    frame,
                    cornerRadius = mediaFrameCorner(item.shot),
                    videoAllowed = !LocalReducedMotion.current,
                )
            } else {
                ViewerMediaImage(item, playing = false, modifier = frame)
            }
        }
        onTogglePlay?.let { toggle -> PlayPauseButton(playing = playing, onToggle = toggle) }
    }
}

/**
 * The detail screen's bar: back, the project's title so a visitor deep in the page knows where they are, and the
 * theme switch. Its divider is decided in the draw phase from [scrolled], like the page's own bar.
 */
@Composable
private fun DetailTopBar(title: String, onBack: () -> Unit, gutter: Dp, scrolled: () -> Boolean) {
    val background = MaterialTheme.colorScheme.background
    val divider = MaterialTheme.colorScheme.outlineVariant
    Box(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                drawRect(background)
                if (scrolled()) drawLine(divider, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
            },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier.widthIn(max = PageLayout.MaxWidth).fillMaxWidth().height(64.dp).padding(horizontal = gutter - 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(onClick = onBack, modifier = Modifier.semantics { contentDescription = "Back to all projects" }) {
                BackGlyph(MaterialTheme.colorScheme.onBackground)
            }
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            ThemeToggle()
        }
    }
}

private val SectionGap = 40.dp
private val TextColumnMaxWidth = 760.dp
private val PortraitHero = 520.dp
private val PortraitHeroCompact = 440.dp

private val previewProject = Project(
    id = "about-yowkey",
    title = "This portfolio",
    summary = "A Compose Multiplatform app running in your browser through Kotlin/WebAssembly.",
    tagline = "One Compose UI compiled to WebAssembly, with a crawlable HTML twin",
    period = "Oct 2026",
    visibility = RepoVisibility.Public,
    highlights = listOf("Adaptive layout from phone to wide desktop", "Content lives in one JSON file"),
    stack = listOf("Compose Multiplatform", "Kotlin/Wasm", "Koin"),
    links = listOf(Link("Source on GitHub", "https://github.com")),
    // No loader in previews, so media shows as its sized placeholder.
    screenshots = listOf(Screenshot("hero.webp", "The hero", 800, 600), Screenshot("cards.webp", "The cards", 800, 600)),
    engineering = Engineering(
        builtWith = listOf("Compose Multiplatform", "Kotlin/Wasm"),
        platforms = listOf("Web", "Desktop"),
        architecture = "One-way layers: UI, ViewModel, repository, parser, model.",
        decisions = listOf(Decision("Static HTML first", "Crawlers cannot read a canvas."), Decision("Hash router", "Two routes need no library.")),
        modules = listOf(Module("ui", "Compose screens", listOf("model")), Module("model", "Content types")),
        quality = listOf("Unit tests in CI", "Lighthouse on every deploy"),
    ),
)

private val CompactPreviewLayout = PageLayout(
    compact = true, wideSections = false, serviceColumns = 1, projectColumns = 1, skillColumns = 1, gutter = 20.dp,
)
private val ExpandedPreviewLayout = PageLayout(
    compact = false, wideSections = true, serviceColumns = 2, projectColumns = 2, skillColumns = 2, gutter = 48.dp,
)

@Preview(widthDp = 412, heightDp = 2400)
@Composable
private fun CompactDetailPreview() = PortfolioTheme {
    ProjectDetailScreen(previewProject, CompactPreviewLayout, onBack = {})
}

@Preview(widthDp = 1280, heightDp = 1600)
@Composable
private fun ExpandedDetailDarkPreview() = PortfolioTheme(darkTheme = true) {
    ProjectDetailScreen(previewProject, ExpandedPreviewLayout, onBack = {})
}

/** Text only: no media, no engineering. One centred column even on a wide window. */
@Preview(widthDp = 1280, heightDp = 900)
@Composable
private fun TextOnlyDetailPreview() = PortfolioTheme {
    ProjectDetailScreen(
        Project("automation", "Workflow automation", "A self-hosted control platform.", highlights = listOf("Confirm-first Git workflow")),
        ExpandedPreviewLayout,
        onBack = {},
    )
}

/** Work: figures, impact, contributions, optimizations and audits, then how it is built. */
@Preview(widthDp = 412, heightDp = 2000)
@Composable
private fun WorkDetailPreview() = PortfolioTheme {
    ProjectDetailScreen(
        previewProject.copy(
            id = "arenaplus",
            title = "ArenaPlus",
            visibility = null,
            screenshots = emptyList(),
            links = emptyList(),
            company = "DigiPlus",
            role = "Android developer",
            figures = listOf(ProofPoint("53%", "of V2's code, written by me"), ProofPoint("270K+", "lines across 413 commits")),
            impact = listOf("The app runs native-only instead of inside a WebView."),
            optimizations = listOf("Cut cold-start work on the main thread", "Audited the minified release build"),
        ),
        CompactPreviewLayout,
        onBack = {},
    )
}
