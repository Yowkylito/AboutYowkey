package dev.yowkey.portfolio.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.yowkey.portfolio.model.Portfolio
import dev.yowkey.portfolio.model.Project
import dev.yowkey.portfolio.ui.components.SectionHeader
import dev.yowkey.portfolio.ui.components.RevealTracker
import dev.yowkey.portfolio.ui.components.entrance
import dev.yowkey.portfolio.ui.components.rememberRevealProgress
import dev.yowkey.portfolio.ui.image.ScreenshotViewer
import dev.yowkey.portfolio.ui.image.ViewerRequest
import dev.yowkey.portfolio.ui.sections.AwardList
import dev.yowkey.portfolio.ui.sections.CardRow
import dev.yowkey.portfolio.ui.sections.ContactBlock
import dev.yowkey.portfolio.ui.sections.Footer
import dev.yowkey.portfolio.ui.sections.Hero
import dev.yowkey.portfolio.ui.sections.JobEntry
import dev.yowkey.portfolio.ui.sections.ProjectCard
import dev.yowkey.portfolio.ui.sections.ServiceCard
import dev.yowkey.portfolio.ui.sections.SkillGroups
import dev.yowkey.portfolio.ui.theme.LocalMotion
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

/** Column counts and spacing for the current window, derived from its size class, never from raw dp checks. */
data class PageLayout(
    val compact: Boolean,
    val wideSections: Boolean,
    val serviceColumns: Int,
    val projectColumns: Int,
    val skillColumns: Int,
    val gutter: Dp,
) {
    companion object {
        val MaxWidth = 1120.dp

        fun from(sizeClass: WindowSizeClass): PageLayout {
            val medium = sizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)
            val expanded = sizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)
            return PageLayout(
                compact = !medium,
                wideSections = expanded,
                serviceColumns = if (medium) 2 else 1,
                projectColumns = if (medium) 2 else 1,
                skillColumns = if (medium) 2 else 1,
                gutter = when {
                    expanded -> 48.dp
                    medium -> 32.dp
                    else -> 20.dp
                },
            )
        }
    }
}

/** Stateful entry: owns the ViewModel. Everything below it is stateless and previewable. */
@Composable
fun PortfolioRoute(
    platformLabel: String,
    onContentShown: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PortfolioViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    PortfolioScreen(
        state = state,
        platformLabel = platformLabel,
        onRetry = viewModel::retry,
        onContentShown = onContentShown,
        modifier = modifier,
    )
}

@Composable
fun PortfolioScreen(
    state: PortfolioUiState,
    platformLabel: String,
    onRetry: () -> Unit,
    onContentShown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (state) {
            PortfolioUiState.Loading -> LoadingPage()
            is PortfolioUiState.Error -> ErrorPage(state.message, onRetry)
            is PortfolioUiState.Ready -> {
                PortfolioPage(state.portfolio, platformLabel)
                val shown by rememberUpdatedState(onContentShown)
                LaunchedEffect(Unit) {
                    withFrameNanos { } // after the first frame of real content is on screen
                    shown()
                }
            }
        }
    }
}

@Composable
fun PortfolioPage(portfolio: Portfolio, platformLabel: String, modifier: Modifier = Modifier) {
    val layout = PageLayout.from(currentWindowAdaptiveInfoV2().windowSizeClass)
    val items = remember(portfolio, layout) { buildPageItems(portfolio, layout.serviceColumns, layout.projectColumns) }
    val starts = remember(items) { sectionStarts(items) }
    val numbered = remember(starts) { starts.keys.filter { it != Section.Home } }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val activeSection by remember(items) {
        derivedStateOf {
            // At the very bottom the short Contact section can never reach the top, so it would never light up.
            if (!listState.canScrollForward && listState.firstVisibleItemIndex > 0) Section.Contact
            else items.getOrNull(listState.firstVisibleItemIndex)?.section ?: Section.Home
        }
    }
    val motion = LocalMotion.current
    val navigate: (Section) -> Unit = remember(starts, motion) {
        { section ->
            starts[section]?.let {
                scope.launch { if (motion.reduced) listState.scrollToItem(it) else listState.animateScrollToItem(it) }
            }
        }
    }
    // Hoisted and keyed by item key, so an entrance plays once per row however often it scrolls in and out.
    val reveals = remember { RevealTracker() }
    var viewer by remember { mutableStateOf<ViewerRequest?>(null) }
    val openScreenshot: (Project, Int, Rect?) -> Unit = remember {
        { project, index, origin -> viewer = ViewerRequest(project.title, project.screenshots, index, origin) }
    }
    // Read only inside the carousels' autoplay flows, so opening the viewer recomposes nothing below.
    val canAutoplay: () -> Boolean = remember { { viewer == null } }

    viewer?.let { ScreenshotViewer(it, onDismiss = { viewer = null }) }

    Column(modifier.fillMaxSize()) {
        TopBar(
            name = portfolio.profile.name,
            sections = numbered,
            active = activeSection,
            // Six links need the expanded width; below it they move into the menu.
            compact = !layout.wideSections,
            gutter = layout.gutter,
            maxWidth = PageLayout.MaxWidth,
            scrolled = { listState.canScrollBackward },
            onNavigate = navigate,
            modifier = Modifier.zIndex(1f), // its shadow falls over the list
        )
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            items(items, key = { it.key }, contentType = { it::class.simpleName }) { item ->
                val progress = rememberRevealProgress(item.key, reveals, motion)
                val reveal = remember(progress) { { progress.value } }
                // Card grids stagger their cards (and header) themselves; every other row enters as one piece.
                val staggersCards = item is PageItem.Services || item is PageItem.Projects
                Box(
                    Modifier
                        .fillMaxWidth()
                        .then(if (staggersCards) Modifier else Modifier.entrance(reveal, motion)),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Box(
                        Modifier
                            .widthIn(max = PageLayout.MaxWidth)
                            .fillMaxWidth()
                            .padding(horizontal = layout.gutter)
                            .padding(top = item.topSpacing(layout)),
                    ) {
                        PageRow(
                            item = item,
                            layout = layout,
                            platformLabel = platformLabel,
                            number = numbered.indexOf(item.section) + 1,
                            reveal = if (staggersCards) reveal else null,
                            onNavigate = navigate,
                            onOpenScreenshot = openScreenshot,
                            canAutoplay = canAutoplay,
                        )
                    }
                }
            }
        }
    }
}

private fun PageItem.topSpacing(layout: PageLayout): Dp = when {
    this is PageItem.Hero -> if (layout.compact) 40.dp else 96.dp
    this is PageItem.Footer -> 96.dp
    showsHeader -> if (layout.compact) 72.dp else 120.dp
    this is PageItem.Job -> 0.dp
    else -> 16.dp
}

/** [reveal] is set for card-grid rows: their header and each card enter on it, cards slightly after one another. */
@Composable
private fun PageRow(
    item: PageItem,
    layout: PageLayout,
    platformLabel: String,
    number: Int,
    reveal: (() -> Float)?,
    onNavigate: (Section) -> Unit,
    onOpenScreenshot: (Project, Int, Rect?) -> Unit,
    canAutoplay: () -> Boolean,
) {
    val motion = LocalMotion.current
    val cardEntrance: (Int) -> Modifier = { i -> reveal?.let { Modifier.entrance(it, motion, lag = i * CardLag) } ?: Modifier }
    val headerEntrance = reveal?.let { Modifier.entrance(it, motion) } ?: Modifier
    when (item) {
        is PageItem.Hero -> SelectionContainer {
            Hero(
                profile = item.profile,
                platformLabel = platformLabel,
                compact = layout.compact,
                onSeeProjects = { onNavigate(Section.Projects) },
                onContact = { onNavigate(Section.Contact) },
            )
        }
        is PageItem.Services -> SectionFrame(item, number, layout, headerEntrance) {
            CardRow(item.columns, item.services.size, entrance = cardEntrance) { i, m -> ServiceCard(item.services[i], m) }
        }
        is PageItem.Projects -> SectionFrame(item, number, layout, headerEntrance) {
            CardRow(item.columns, item.projects.size, entrance = cardEntrance) { i, m ->
                val project = item.projects[i]
                ProjectCard(
                    project,
                    onOpenScreenshot = { index, origin -> onOpenScreenshot(project, index, origin) },
                    modifier = m,
                    canAutoplay = canAutoplay,
                )
            }
        }
        is PageItem.Recognition -> SectionFrame(item, number, layout) {
            AwardList(item.awards, columns = if (layout.compact) 1 else 2)
        }
        is PageItem.Job -> SectionFrame(item, number, layout) {
            JobEntry(item.experience, item.isLast, wide = !layout.compact)
        }
        is PageItem.Skills -> SectionFrame(item, number, layout) {
            SkillGroups(item.groups, layout.skillColumns)
        }
        is PageItem.Contact -> SectionFrame(item, number, layout) {
            ContactBlock(item.email, item.links)
        }
        PageItem.Footer -> Footer(platformLabel)
    }
}

/** How far each card in a row trails the one before it, as a fraction of the shared entrance. */
private const val CardLag = 0.15f

/** Wide windows put the section header in a left rail beside its rows; narrower ones stack it on top. */
@Composable
private fun SectionFrame(
    item: PageItem,
    number: Int,
    layout: PageLayout,
    headerModifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (layout.wideSections) {
        Row(Modifier.fillMaxWidth()) {
            Box(Modifier.width(240.dp)) {
                if (item.showsHeader) SectionHeader(number, item.section.title, headerModifier)
            }
            Box(Modifier.weight(1f)) { content() }
        }
    } else {
        Column(Modifier.fillMaxWidth()) {
            if (item.showsHeader) {
                SectionHeader(number, item.section.title, headerModifier)
                Spacer(Modifier.height(28.dp))
            }
            content()
        }
    }
}

@Composable
fun LoadingPage(modifier: Modifier = Modifier) {
    val reducedMotion = LocalMotion.current.reduced
    val pulse = rememberInfiniteTransition(label = "skeleton")
    val alpha by pulse.animateFloat(
        initialValue = 0.45f,
        targetValue = if (reducedMotion) 0.45f else 0.9f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "skeletonAlpha",
    )
    val block = MaterialTheme.colorScheme.surfaceVariant
    Column(
        modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 96.dp)
            .graphicsLayer { this.alpha = alpha }
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        listOf(0.25f, 0.6f, 0.9f, 0.75f).forEach { fraction ->
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .height(if (fraction == 0.6f) 56.dp else 20.dp)
                    .background(block, RoundedCornerShape(8.dp)),
            )
        }
    }
}

@Composable
fun ErrorPage(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 480.dp).semantics { liveRegion = LiveRegionMode.Assertive },
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("The portfolio could not be loaded.", style = MaterialTheme.typography.titleLarge)
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onRetry) { Text("Try again") }
        }
    }
}
