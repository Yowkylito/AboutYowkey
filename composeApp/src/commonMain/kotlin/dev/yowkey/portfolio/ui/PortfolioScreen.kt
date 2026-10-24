package dev.yowkey.portfolio.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import dev.yowkey.portfolio.model.FeedbackDraft
import dev.yowkey.portfolio.model.Link
import dev.yowkey.portfolio.model.LiveStats
import dev.yowkey.portfolio.model.MessageDraft
import dev.yowkey.portfolio.model.Portfolio
import dev.yowkey.portfolio.model.Relation
import dev.yowkey.portfolio.model.SiteNotes
import dev.yowkey.portfolio.model.VisitCount
import dev.yowkey.portfolio.ui.components.BackToTop
import dev.yowkey.portfolio.ui.components.ProofReveal
import dev.yowkey.portfolio.ui.components.SectionHeader
import dev.yowkey.portfolio.ui.components.rememberProofReveal
import dev.yowkey.portfolio.ui.components.rememberRevealProgress
import dev.yowkey.portfolio.ui.sections.AwardList
import dev.yowkey.portfolio.ui.sections.CardRow
import dev.yowkey.portfolio.ui.sections.ContactBlock
import dev.yowkey.portfolio.ui.sections.FeedbackForm
import dev.yowkey.portfolio.ui.sections.Footer
import dev.yowkey.portfolio.ui.sections.GroupHeader
import dev.yowkey.portfolio.ui.sections.Hero
import dev.yowkey.portfolio.ui.sections.JobEntry
import dev.yowkey.portfolio.ui.sections.MessageForm
import dev.yowkey.portfolio.ui.sections.MoreNotesButton
import dev.yowkey.portfolio.ui.sections.NoteCards
import dev.yowkey.portfolio.ui.sections.ProjectCard
import dev.yowkey.portfolio.ui.sections.RatingsDashboard
import dev.yowkey.portfolio.ui.sections.ServiceCard
import dev.yowkey.portfolio.ui.sections.SkillGroups
import dev.yowkey.portfolio.ui.sections.StarNotesSheet
import dev.yowkey.portfolio.ui.sections.WorkCard
import dev.yowkey.portfolio.ui.detail.ProjectDetailScreen
import dev.yowkey.portfolio.ui.navigation.Navigator
import dev.yowkey.portfolio.ui.navigation.PageReading
import dev.yowkey.portfolio.ui.navigation.ReadingFrame
import dev.yowkey.portfolio.ui.navigation.Route
import dev.yowkey.portfolio.ui.navigation.formFocus
import dev.yowkey.portfolio.ui.navigation.rememberBackToTopShown
import dev.yowkey.portfolio.ui.navigation.rememberPageReading
import dev.yowkey.portfolio.ui.theme.LocalReducedMotion
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import dev.yowkey.portfolio.ui.components.LocalBottomEndClearance

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

/** The note form's state and its two actions, handed to the Contact section's row together. */
@Immutable
data class FeedbackControls(
    val state: FeedbackUiState = FeedbackUiState.Idle,
    val onSend: (FeedbackDraft) -> Unit = {},
    val onReset: () -> Unit = {},
)

/**
 * How the visitor reads the notes: "Show more" under the list ([loading] while a page is on its way), and the star
 * sheet a dashboard bar opens ([sheet], null while closed), with what its sorter and its own "Show more" do.
 */
@Immutable
data class NotesControls(
    val loading: Boolean = false,
    val onMore: () -> Unit = {},
    val sheet: StarSheet? = null,
    val onOpenSheet: (Int) -> Unit = {},
    val onCloseSheet: () -> Unit = {},
    val onPickRelation: (Relation?) -> Unit = {},
    val onMoreInSheet: () -> Unit = {},
)

/** The private message form's state and its two actions, as [FeedbackControls] are the note form's. */
@Immutable
data class MessageControls(
    val state: MessageUiState = MessageUiState.Idle,
    val onSend: (MessageDraft) -> Unit = {},
    val onReset: () -> Unit = {},
)

/** Stateful entry: owns the ViewModel and the router. Everything below it is stateless and previewable. */
@Composable
fun PortfolioRoute(
    platformLabel: String,
    onContentShown: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PortfolioViewModel = koinViewModel(),
    navigator: Navigator = koinInject(),
) {
    val state by viewModel.state.collectAsState()
    val route by navigator.route.collectAsState()
    val visits by viewModel.visits.collectAsState()
    val notes by viewModel.notes.collectAsState()
    val notesLoading by viewModel.notesLoading.collectAsState()
    val sheet by viewModel.sheet.collectAsState()
    val onMoreNotes: () -> Unit = remember(viewModel) { viewModel::showMoreNotes }
    val onOpenSheet: (Int) -> Unit = remember(viewModel) { viewModel::openSheet }
    val onCloseSheet: () -> Unit = remember(viewModel) { viewModel::closeSheet }
    val onPickRelation: (Relation?) -> Unit = remember(viewModel) { viewModel::pickRelation }
    val onMoreInSheet: () -> Unit = remember(viewModel) { viewModel::showMoreInSheet }
    val feedbackState by viewModel.feedback.collectAsState()
    val onSendFeedback: (FeedbackDraft) -> Unit = remember(viewModel) { viewModel::sendFeedback }
    val onResetFeedback: () -> Unit = remember(viewModel) { viewModel::resetFeedback }
    val messageState by viewModel.message.collectAsState()
    val onSendMessage: (MessageDraft) -> Unit = remember(viewModel) { viewModel::sendMessage }
    val onResetMessage: () -> Unit = remember(viewModel) { viewModel::resetMessage }
    val onOpenLink: (Link) -> Unit = remember(viewModel) { viewModel::trackClick }
    val onOpenProject: (String) -> Unit = remember(navigator) { { id -> navigator.open(Route.Project(id)) } }
    val onBack: () -> Unit = remember(navigator) { { navigator.back() } }
    val onUnknownProject: () -> Unit = remember(navigator) { { navigator.replace(Route.Home) } }
    PortfolioScreen(
        state = state,
        route = route,
        platformLabel = platformLabel,
        onRetry = viewModel::retry,
        onOpenProject = onOpenProject,
        onBack = onBack,
        onUnknownProject = onUnknownProject,
        onContentShown = onContentShown,
        modifier = modifier,
        visits = visits,
        notes = notes,
        feedback = FeedbackControls(feedbackState, onSendFeedback, onResetFeedback),
        message = MessageControls(messageState, onSendMessage, onResetMessage),
        onOpenLink = onOpenLink,
        notesControls = NotesControls(notesLoading, onMoreNotes, sheet, onOpenSheet, onCloseSheet, onPickRelation, onMoreInSheet),
    )
}

/**
 * @param route which screen to show once content is ready; a project id that is not in the content calls
 *   [onUnknownProject] (the router then goes home) and shows the list meanwhile
 * @param visits the bar's count, once the site's backend has answered; null until then, or without a backend
 * @param notes the rating and the public notes shown so far, for the bar's proof, the dashboard and the cards above
 *   the form; null likewise
 * @param notesControls "Show more" and the star sheet the dashboard's bars open, with what they are showing
 * @param feedback the note form's state and actions; the form only exists when the content names a backend
 * @param message the private message form's state and actions; likewise only with a backend
 * @param onOpenLink called once a profile link in the Contact section has opened, to count LinkedIn and GitHub
 */
@Composable
fun PortfolioScreen(
    state: PortfolioUiState,
    route: Route,
    platformLabel: String,
    onRetry: () -> Unit,
    onOpenProject: (String) -> Unit,
    onBack: () -> Unit,
    onUnknownProject: () -> Unit,
    onContentShown: () -> Unit,
    modifier: Modifier = Modifier,
    visits: VisitCount? = null,
    notes: SiteNotes? = null,
    feedback: FeedbackControls = FeedbackControls(),
    message: MessageControls = MessageControls(),
    onOpenLink: (Link) -> Unit = {},
    notesControls: NotesControls = NotesControls(),
) {
    Surface(modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when (state) {
            PortfolioUiState.Loading -> LoadingPage()
            is PortfolioUiState.Error -> ErrorPage(state.message, onRetry)
            is PortfolioUiState.Ready -> {
                PortfolioContent(
                    state.portfolio, route, platformLabel, visits, notes, feedback, message, onOpenLink, onOpenProject, onBack,
                    onUnknownProject, notesControls,
                )
                val shown by rememberUpdatedState(onContentShown)
                LaunchedEffect(Unit) {
                    withFrameNanos { } // after the first frame of real content is on screen
                    shown()
                }
            }
        }
    }
}

/**
 * The list and the detail screen, switched by [route]. The list's scroll position, its reveal bookkeeping (the bar's
 * proof's too), its reading keys and whether Back to top shows live here, above the switch, so coming back from a
 * project lands exactly where the visitor left (the list is rebuilt on the same state, which keeps the first row's
 * index and offset), with nothing re-animating. Opening a project rises and fades in over 280 ms; going back fades it
 * out. Under reduced motion the switch is instant.
 */
@Composable
private fun PortfolioContent(
    portfolio: Portfolio,
    route: Route,
    platformLabel: String,
    visits: VisitCount?,
    notes: SiteNotes?,
    feedback: FeedbackControls,
    message: MessageControls,
    onOpenLink: (Link) -> Unit,
    onOpenProject: (String) -> Unit,
    onBack: () -> Unit,
    onUnknownProject: () -> Unit,
    notesControls: NotesControls,
) {
    val layout = PageLayout.from(currentWindowAdaptiveInfoV2().windowSizeClass)
    val listState = rememberLazyListState()
    val reading = rememberPageReading(listState)
    val backToTop = rememberBackToTopShown(listState)
    // The button's corner (its 48dp, the gutter around it, a margin, and any host button it floats above), which no
    // playing cover may pass under while the button shows: see CoverPlayback.clearOf.
    val keepClearPx = with(LocalDensity.current) {
        (layout.gutter + 48.dp + 16.dp + LocalBottomEndClearance.current).roundToPx()
    }
    val seen = remember { mutableSetOf<Any>() }
    val proofReveal = rememberProofReveal()
    val reducedMotion = LocalReducedMotion.current
    val project = remember(portfolio, route) {
        (route as? Route.Project)?.let { r -> portfolio.projects.firstOrNull { it.id == r.id } }
    }
    if (route is Route.Project && project == null) {
        val unknown by rememberUpdatedState(onUnknownProject)
        LaunchedEffect(route) { unknown() }
    }
    val showingList = project == null
    AnimatedContent(
        targetState = project,
        transitionSpec = {
            when {
                reducedMotion -> EnterTransition.None togetherWith ExitTransition.None
                targetState != null ->
                    (fadeIn(tween(220, delayMillis = 40)) + slideInVertically(tween(280, easing = EaseOutCubic)) { it / 14 })
                        .togetherWith(fadeOut(tween(120)))
                else -> fadeIn(tween(200)).togetherWith(fadeOut(tween(160)) + slideOutVertically(tween(200)) { it / 20 })
            }
        },
        contentKey = { it?.id },
        label = "screen",
    ) { shown ->
        if (shown == null) {
            // The star sheet covers the page: the keys and Back to top stand aside while it is open.
            val uncovered = showingList && notesControls.sheet == null
            ReadingFrame(reading, enabled = uncovered) {
                PortfolioPage(
                    portfolio = portfolio,
                    platformLabel = platformLabel,
                    layout = layout,
                    listState = listState,
                    reading = reading,
                    seen = seen,
                    // Read from the current route, not this child's: the list fading out must stop its covers now.
                    coversActive = showingList && !reducedMotion,
                    visits = visits,
                    notes = notes,
                    feedback = feedback,
                    onOpenProject = onOpenProject,
                    message = message,
                    onOpenLink = onOpenLink,
                    notesControls = notesControls,
                    coverKeepClear = { if (backToTop.value && uncovered) keepClearPx else 0 },
                    proofReveal = proofReveal,
                )
                BackToTop(
                    visible = { backToTop.value && uncovered },
                    onClick = { reading.backToTop(reducedMotion) },
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = layout.gutter, bottom = layout.gutter),
                )
            }
        } else {
            ProjectDetailScreen(shown, layout, onBack = onBack)
        }
    }
}

/**
 * The page's list. [reading] runs its section jumps and learns from its form rows when the focus is in a form (see
 * [ReadingFrame], which hosts this). [proofReveal] is the bar's proof's entrance, kept by the caller so it plays once.
 */
@Composable
fun PortfolioPage(
    portfolio: Portfolio,
    platformLabel: String,
    layout: PageLayout,
    listState: LazyListState,
    reading: PageReading,
    seen: MutableSet<Any>,
    coversActive: Boolean,
    visits: VisitCount?,
    notes: SiteNotes?,
    feedback: FeedbackControls,
    onOpenProject: (String) -> Unit,
    modifier: Modifier = Modifier,
    message: MessageControls = MessageControls(),
    onOpenLink: (Link) -> Unit = {},
    notesControls: NotesControls = NotesControls(),
    coverKeepClear: () -> Int = { 0 },
    proofReveal: ProofReveal = rememberProofReveal(),
) {
    val items = remember(portfolio, layout, notes) {
        buildPageItems(
            portfolio, layout.serviceColumns, layout.projectColumns, notes?.notes.orEmpty(),
            notesLoaded = notes != null, moreNotes = notes?.more == true,
        )
    }
    val stats = LiveStats(visits, notes?.ratings)
    val starts = remember(items) { sectionStarts(items) }
    val numbered = remember(starts) { starts.keys.filter { it != Section.Home } }
    val reducedMotion = LocalReducedMotion.current
    val density = LocalDensity.current
    val activeSection by remember(items) {
        derivedStateOf {
            // At the very bottom the short Contact section can never reach the top, so it would never light up.
            if (!listState.canScrollForward && listState.firstVisibleItemIndex > 0) Section.Contact
            else items.getOrNull(listState.firstVisibleItemIndex)?.section ?: Section.Home
        }
    }
    // A glide whose time scales with the distance, landing exactly on the row's header (see glideToItem).
    val jumpTo: (Int) -> Unit = remember(items, layout, density, reducedMotion, reading) {
        { index -> reading.jumpTo(index, with(density) { items[index].landingInto(layout).roundToPx() }, reducedMotion) }
    }
    val navigate: (Section) -> Unit = remember(starts, jumpTo) { { section -> starts[section]?.let(jumpTo) } }
    // The bar's proof leads to the notes, which it sums up: their dashboard, landing as a section's header does.
    val openNotes: () -> Unit = remember(items, jumpTo) { { notesRow(items)?.let(jumpTo) } }
    val playingCovers = rememberPlayingCovers(listState, items, coversActive, coverKeepClear)
    val isCoverPlaying: (String) -> Boolean = remember(playingCovers) { { id -> id in playingCovers.value } }

    Column(modifier.fillMaxSize()) {
        TopBar(
            profile = portfolio.profile,
            sections = numbered,
            active = activeSection,
            // Six links need the expanded width; below it they move into the menu.
            compact = !layout.wideSections,
            gutter = layout.gutter,
            maxWidth = PageLayout.MaxWidth,
            scrolled = { listState.canScrollBackward },
            // A derived state: the bar hears of it only when it flips. The progress is read in the bar's draw phase.
            heroPassed = rememberHeroPassed(listState),
            progress = { listState.layoutInfo.readingProgress() },
            stats = stats,
            onOpenNotes = openNotes,
            onNavigate = navigate,
            proofReveal = proofReveal,
        )
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            items(items, key = { it.key }, contentType = { it::class.simpleName }) { item ->
                val progress = rememberRevealProgress(item.key, seen, reducedMotion)
                // A form row scrolled away with the focus in it goes without a word: let the reading keys back in.
                if (item.holdsForm) DisposableEffect(item.key) { onDispose { reading.formFocusChanged(item.key, focused = false) } }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .then(if (item.holdsForm) Modifier.formFocus(reading, item.key) else Modifier)
                        .graphicsLayer {
                            alpha = progress.value
                            translationY = (1f - progress.value) * 28.dp.toPx()
                        },
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
                            item, layout, platformLabel, numbered.indexOf(item.section) + 1, navigate, onOpenProject, isCoverPlaying,
                            feedback, message, onOpenLink, notes, notesControls,
                        )
                    }
                }
            }
        }
    }
    // Over the page, not in the dashboard's row: the sheet stays whatever the list does underneath.
    notesControls.sheet?.let { sheet ->
        StarNotesSheet(
            sheet = sheet,
            ratings = sheet.page?.ratings ?: notes?.ratings,
            onPick = notesControls.onPickRelation,
            onMore = notesControls.onMoreInSheet,
            onClose = notesControls.onCloseSheet,
        )
    }
}

private fun PageItem.topSpacing(layout: PageLayout): Dp = when {
    this is PageItem.Hero -> if (layout.compact) 40.dp else 96.dp
    this is PageItem.Footer -> 96.dp
    // The message form, the notes' dashboard, the note form: apart from what is above each, closer than a new section.
    this is PageItem.Message || this is PageItem.Ratings || this is PageItem.Feedback -> if (layout.compact) 48.dp else 64.dp
    this is PageItem.Notes && row == 0 -> 24.dp
    this is PageItem.MoreNotes -> 24.dp
    showsHeader -> if (layout.compact) 72.dp else 120.dp
    // The second group of a section: clearly apart from the first group's last row, closer than a new section.
    this is PageItem.Projects && showsGroupHeader -> if (layout.compact) 56.dp else 72.dp
    this is PageItem.Job -> 0.dp
    else -> 16.dp
}

@Composable
private fun PageRow(
    item: PageItem,
    layout: PageLayout,
    platformLabel: String,
    number: Int,
    onNavigate: (Section) -> Unit,
    onOpenProject: (String) -> Unit,
    isCoverPlaying: (String) -> Boolean,
    feedback: FeedbackControls,
    message: MessageControls,
    onOpenLink: (Link) -> Unit,
    notes: SiteNotes?,
    notesControls: NotesControls,
) {
    when (item) {
        is PageItem.Hero -> SelectionContainer {
            Hero(
                profile = item.profile,
                compact = layout.compact,
                wide = layout.wideSections,
                onSeeProjects = { onNavigate(Section.Projects) },
                onContact = { onNavigate(Section.Contact) },
            )
        }
        is PageItem.Services -> SectionFrame(item, number, layout) {
            CardRow(item.columns, item.services.size) { i, m -> ServiceCard(item.services[i], m) }
        }
        is PageItem.Projects -> SectionFrame(item, number, layout) {
            Column {
                if (item.showsGroupHeader) GroupHeader(item.group.title, item.note, Modifier.padding(bottom = 24.dp))
                CardRow(item.columns, item.projects.size) { i, m ->
                    val project = item.projects[i]
                    if (project.isWork) {
                        WorkCard(project, onOpen = { onOpenProject(project.id) }, modifier = m)
                    } else {
                        ProjectCard(
                            project,
                            playing = { isCoverPlaying(project.id) },
                            onOpen = { onOpenProject(project.id) },
                            modifier = m,
                        )
                    }
                }
            }
        }
        is PageItem.Recognition -> SectionFrame(item, number, layout) {
            AwardList(item.awards, wide = !layout.compact)
        }
        is PageItem.Job -> SectionFrame(item, number, layout) {
            JobEntry(item.experience, item.isLast, wide = !layout.compact)
        }
        is PageItem.Skills -> SectionFrame(item, number, layout) {
            SkillGroups(item.groups, layout.skillColumns)
        }
        is PageItem.Contact -> SectionFrame(item, number, layout) {
            ContactBlock(item.email, item.links, onOpenLink = onOpenLink)
        }
        PageItem.Message -> SectionFrame(item, number, layout) {
            MessageForm(message.state, message.onSend, message.onReset, wide = !layout.compact)
        }
        PageItem.Ratings -> SectionFrame(item, number, layout) {
            if (notes != null) RatingsDashboard(notes, notesControls.loading, notesControls.onOpenSheet, wide = !layout.compact)
        }
        is PageItem.Notes -> SectionFrame(item, number, layout) {
            NoteCards(item.notes, item.columns)
        }
        PageItem.MoreNotes -> SectionFrame(item, number, layout) {
            MoreNotesButton(notesControls.loading, notesControls.onMore)
        }
        PageItem.Feedback -> SectionFrame(item, number, layout) {
            FeedbackForm(feedback.state, feedback.onSend, feedback.onReset, wide = !layout.compact)
        }
        PageItem.Footer -> Footer(platformLabel)
    }
}

/** Wide windows put the section header in a left rail beside its rows; narrower ones stack it on top. */
@Composable
private fun SectionFrame(
    item: PageItem,
    number: Int,
    layout: PageLayout,
    content: @Composable () -> Unit,
) {
    if (layout.wideSections) {
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.width(240.dp).padding(end = 24.dp)) {
                if (item.showsHeader) SectionHeader(number, item.section.title)
            }
            Box(Modifier.weight(1f)) { content() }
        }
    } else {
        Column(Modifier.fillMaxWidth()) {
            if (item.showsHeader) {
                SectionHeader(number, item.section.title)
                Spacer(Modifier.height(28.dp))
            }
            content()
        }
    }
}

private val EaseOutCubic = CubicBezierEasing(0.33f, 1f, 0.68f, 1f)

/** How far under the bar a section's header lands after a jump: close enough to read as arrived, with room to breathe. */
private val HeaderLanding = 32.dp

/**
 * How far into its first row a jump to a section stops: past the row's top spacing but for [HeaderLanding], so the
 * header lands just under the bar rather than a whole section gap below it. The hero is the top of the page.
 */
private fun PageItem.landingInto(layout: PageLayout): Dp =
    if (this is PageItem.Hero) 0.dp else (topSpacing(layout) - HeaderLanding).coerceAtLeast(0.dp)

/**
 * Where the bar's proof leads: the notes' dashboard, once the backend has answered with them; before that, the note
 * form; null without a backend, which has no proof to show either.
 */
internal fun notesRow(items: List<PageItem>): Int? =
    items.indexOf(PageItem.Ratings).takeIf { it >= 0 } ?: items.indexOf(PageItem.Feedback).takeIf { it >= 0 }

/** The rows with text fields: while the focus is in one, the reading keys leave its keys alone. */
private val PageItem.holdsForm: Boolean get() = this == PageItem.Message || this == PageItem.Feedback

@Composable
fun LoadingPage(modifier: Modifier = Modifier) {
    val reducedMotion = LocalReducedMotion.current
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
