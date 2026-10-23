package dev.yowkey.portfolio.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.yowkey.portfolio.data.PortfolioRepository
import dev.yowkey.portfolio.data.SiteApi
import dev.yowkey.portfolio.model.FeedbackDraft
import dev.yowkey.portfolio.model.Link
import dev.yowkey.portfolio.model.MessageDraft
import dev.yowkey.portfolio.model.Portfolio
import dev.yowkey.portfolio.model.PublicNote
import dev.yowkey.portfolio.model.Relation
import dev.yowkey.portfolio.model.SiteNotes
import dev.yowkey.portfolio.model.VisitCount
import dev.yowkey.portfolio.model.trackingKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
sealed interface PortfolioUiState {
    data object Loading : PortfolioUiState
    data class Ready(val portfolio: Portfolio) : PortfolioUiState
    data class Error(val message: String) : PortfolioUiState
}

/** The note form's state, from the empty form to the thank-you. */
@Immutable
sealed interface FeedbackUiState {
    data object Idle : FeedbackUiState
    data object Sending : FeedbackUiState
    data object Sent : FeedbackUiState

    /** [message] is a sentence for the visitor: what was wrong with the note, or that the server was out of reach. */
    data class Failed(val message: String) : FeedbackUiState
}

/**
 * The sheet a dashboard bar opens: [stars]'s public notes, sorted by how their writers know the owner ([relation];
 * null for all of them). [page] is every page of the pick loaded so far, with the sorter's counts; null until the
 * first answers. While [loading] a new pick, the last pick's page stays, so the chips keep their counts; a failure
 * goes back to that pick. [loadingMore] is "Show more" on its way.
 */
@Immutable
data class StarSheet(
    val stars: Int,
    val relation: Relation? = null,
    val page: SiteNotes? = null,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
) {
    /** How many of the star's notes are public, whatever the pick: the sorter's counts added up. */
    val readable: Int
        get() = page?.let { if (it.relations.isEmpty()) it.total else it.relations.values.sum() } ?: 0

    /** The count beside [relation]'s chip: [readable] for All. */
    fun countOf(relation: Relation?): Int = if (relation == null) readable else page?.countOf(relation) ?: 0
}

/** The private message form's state, the same steps as the note form's. */
@Immutable
sealed interface MessageUiState {
    data object Idle : MessageUiState
    data object Sending : MessageUiState
    data object Sent : MessageUiState

    /** [message] is a sentence for the visitor: what was wrong with the message, or that the server was out of reach. */
    data class Failed(val message: String) : MessageUiState
}

class PortfolioViewModel(
    private val repository: PortfolioRepository,
    private val siteApi: SiteApi,
) : ViewModel() {

    private val _state = MutableStateFlow<PortfolioUiState>(PortfolioUiState.Loading)
    val state: StateFlow<PortfolioUiState> = _state.asStateFlow()

    /** The running visit count once the site's backend has answered; null without a backend, or until it does. */
    private val _visits = MutableStateFlow<VisitCount?>(null)
    val visits: StateFlow<VisitCount?> = _visits.asStateFlow()

    /**
     * The rating and the notes under the dashboard (every page loaded so far, pinned first as the backend orders
     * them) once the backend has answered; null without a backend, or until it does.
     */
    private val _notes = MutableStateFlow<SiteNotes?>(null)
    val notes: StateFlow<SiteNotes?> = _notes.asStateFlow()

    /** A page of [notes] is on its way: "Show more", or the list again after a note was sent. */
    private val _notesLoading = MutableStateFlow(false)
    val notesLoading: StateFlow<Boolean> = _notesLoading.asStateFlow()

    /** The star sheet open over the page; null while none is. */
    private val _sheet = MutableStateFlow<StarSheet?>(null)
    val sheet: StateFlow<StarSheet?> = _sheet.asStateFlow()

    private val _feedback = MutableStateFlow<FeedbackUiState>(FeedbackUiState.Idle)
    val feedback: StateFlow<FeedbackUiState> = _feedback.asStateFlow()

    private val _message = MutableStateFlow<MessageUiState>(MessageUiState.Idle)
    val message: StateFlow<MessageUiState> = _message.asStateFlow()

    private var loadJob: Job? = null
    private var visitJob: Job? = null
    private var notesJob: Job? = null
    private var pageJob: Job? = null
    private var sheetJob: Job? = null

    /** The pick the open sheet's page is for, which a failed new pick goes back to. */
    private var sheetPageRelation: Relation? = null
    private var sendJob: Job? = null
    private var messageJob: Job? = null

    /** The tracked links already counted on this page load, by their `trackingKey`. */
    private val clicked = mutableSetOf<String>()

    /**
     * The visitor has sent a note on this page load: from then on every list asks past the browser's copy, which
     * could be from before it, so their note is in each sheet they open, not only in the lists open as it went.
     */
    private var wroteNote = false

    init {
        load()
    }

    /** Safe to call repeatedly (double taps): a load already in flight is not restarted. */
    fun retry() {
        if (loadJob?.isActive == true) return
        load()
    }

    private fun load() {
        _state.value = PortfolioUiState.Loading
        loadJob = viewModelScope.launch {
            _state.value = try {
                val portfolio = repository.load()
                recordVisit(portfolio.api)
                loadNotes(portfolio.api)
                PortfolioUiState.Ready(portfolio)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                PortfolioUiState.Error(e.message ?: "The portfolio could not be loaded.")
            }
        }
    }

    /** Once per page load, whatever retries came before; a failure only leaves the footer without a count. */
    private fun recordVisit(api: String?) {
        if (api == null || visitJob != null) return
        visitJob = viewModelScope.launch {
            _visits.value = try {
                siteApi.recordVisit(api)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * Once per page load as well; without an answer the page simply has no notes and no rating. A list asked for
     * since (a note was sent before it answered) wins over it.
     */
    private fun loadNotes(api: String?) {
        if (api == null || notesJob != null) return
        notesJob = viewModelScope.launch {
            val first = try {
                siteApi.loadNotes(api)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            if (pageJob == null) _notes.value = first
        }
    }

    /** The next page of the notes under the dashboard; nothing while one is on its way or none is left. */
    fun showMoreNotes() {
        val shown = _notes.value ?: return
        if (!shown.more || pageJob?.isActive == true) return
        loadPage(offset = shown.notes.size)
    }

    /**
     * A page of the notes under the dashboard: the first (replacing the list), or the one [offset] notes in (added to
     * it). [fresh] asks past the browser's copy, for a list the visitor has just added to. A failure leaves the list
     * as it was.
     */
    private fun loadPage(offset: Int, fresh: Boolean = wroteNote) {
        val api = (state.value as? PortfolioUiState.Ready)?.portfolio?.api ?: return
        pageJob?.cancel()
        _notesLoading.value = true
        pageJob = viewModelScope.launch {
            try {
                val page = siteApi.loadNotes(api, offset = offset, fresh = fresh)
                val shown = _notes.value
                _notes.value = if (offset == 0 || shown == null) page else page.copy(notes = shown.notes appendNew page.notes)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The list stays as it was; "Show more" is still there to try again.
            }
            _notesLoading.value = false
        }
    }

    /**
     * Opens [stars]'s sheet, sorted by nothing yet (All), and asks for its first page. Without a backend there is no
     * dashboard to open it from, so nothing to do; nor for a number of stars there is no bar for.
     */
    fun openSheet(stars: Int) {
        if (stars !in 1..5 || (state.value as? PortfolioUiState.Ready)?.portfolio?.api == null) return
        _sheet.value = StarSheet(stars, loading = true)
        sheetPageRelation = null
        loadSheet(stars, relation = null, offset = 0)
    }

    /** Closes the sheet; a page still on its way for it is dropped. */
    fun closeSheet() {
        sheetJob?.cancel()
        _sheet.value = null
    }

    /**
     * Sorts the open sheet by [relation] (null for All) and asks for that pick's first page. The pick already
     * showing is left as it is, unless its first page never came (then this tries again).
     */
    fun pickRelation(relation: Relation?) {
        val sheet = _sheet.value ?: return
        if (relation == sheet.relation && sheet.page != null && !sheet.loading) return
        _sheet.value = sheet.copy(relation = relation, loading = true, loadingMore = false)
        loadSheet(sheet.stars, relation, offset = 0)
    }

    /** The open sheet's next page, for its pick; nothing while a page is on its way or none is left. */
    fun showMoreInSheet() {
        val sheet = _sheet.value ?: return
        val page = sheet.page ?: return
        if (!page.more || sheet.loading || sheet.loadingMore) return
        _sheet.value = sheet.copy(loadingMore = true)
        loadSheet(sheet.stars, sheet.relation, offset = page.notes.size)
    }

    /**
     * A page of the sheet's notes: the first of [relation]'s (replacing the list), or the one [offset] notes in
     * (added to it). An answer for a sheet that has since closed, or moved to another pick, is dropped. A failed first
     * page goes back to the pick the page still showing is for; a failed next page leaves it as it was.
     */
    private fun loadSheet(stars: Int, relation: Relation?, offset: Int, fresh: Boolean = wroteNote) {
        val api = (state.value as? PortfolioUiState.Ready)?.portfolio?.api ?: return
        sheetJob?.cancel()
        sheetJob = viewModelScope.launch {
            val page = try {
                siteApi.loadNotes(api, stars, relation?.wire, offset, fresh)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            _sheet.update { sheet ->
                when {
                    sheet == null || sheet.stars != stars || sheet.relation != relation -> sheet
                    page == null && offset == 0 -> sheet.copy(relation = if (sheet.page == null) relation else sheetPageRelation, loading = false)
                    page == null -> sheet.copy(loadingMore = false)
                    offset == 0 || sheet.page == null -> {
                        sheetPageRelation = relation
                        sheet.copy(page = page, loading = false, loadingMore = false)
                    }
                    else -> sheet.copy(page = page.copy(notes = sheet.page.notes appendNew page.notes), loadingMore = false)
                }
            }
        }
    }

    /**
     * Sends [draft] unless one is already on its way; [feedback] follows it. A draft with a problem fails at once,
     * with the problem, and nothing is sent. Without a backend in the content there is no form, so nothing to do.
     */
    fun sendFeedback(draft: FeedbackDraft) {
        val api = (state.value as? PortfolioUiState.Ready)?.portfolio?.api ?: return
        if (sendJob?.isActive == true) return
        draft.problems.firstOrNull()?.let {
            _feedback.value = FeedbackUiState.Failed(it)
            return
        }
        _feedback.value = FeedbackUiState.Sending
        sendJob = viewModelScope.launch {
            _feedback.value = try {
                siteApi.sendFeedback(api, draft)
                // The note is public at once: the list from its first page again, past the browser's copy, and the
                // sheet's too if one is open.
                wroteNote = true
                loadPage(offset = 0)
                _sheet.value?.let { sheet ->
                    _sheet.value = sheet.copy(loading = true, loadingMore = false)
                    loadSheet(sheet.stars, sheet.relation, offset = 0)
                }
                FeedbackUiState.Sent
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                FeedbackUiState.Failed(e.message ?: "The note could not be sent. Try again, or email me.")
            }
        }
    }

    /** Back to the empty form, for another note or once a failure has been read. Ignored while a note is on its way. */
    fun resetFeedback() {
        if (sendJob?.isActive != true) _feedback.value = FeedbackUiState.Idle
    }

    /**
     * Sends the private [draft] unless one is already on its way; [message] follows it, as [feedback] follows a note.
     * A draft with a problem fails at once, with the problem, and nothing is sent. Without a backend there is no form.
     */
    fun sendMessage(draft: MessageDraft) {
        val api = (state.value as? PortfolioUiState.Ready)?.portfolio?.api ?: return
        if (messageJob?.isActive == true) return
        draft.problems.firstOrNull()?.let {
            _message.value = MessageUiState.Failed(it)
            return
        }
        _message.value = MessageUiState.Sending
        messageJob = viewModelScope.launch {
            _message.value = try {
                siteApi.sendMessage(api, draft)
                MessageUiState.Sent
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                MessageUiState.Failed(e.message ?: "The message could not be sent. Try again, or email me.")
            }
        }
    }

    /** Back to the empty form, for another message or once a failure has been read. Ignored mid-flight. */
    fun resetMessage() {
        if (messageJob?.isActive != true) _message.value = MessageUiState.Idle
    }

    /**
     * Counts that [link] was opened, when it is one the backend tracks (LinkedIn, GitHub): at most once per link per
     * page load, as the server counts it at most once a day anyway. Called after the link has opened, and never
     * holds it up: the request runs on its own, and a failure is dropped, since it changes nothing on the page.
     */
    fun trackClick(link: Link) {
        val api = (state.value as? PortfolioUiState.Ready)?.portfolio?.api ?: return
        val key = link.trackingKey ?: return
        if (!clicked.add(key)) return
        viewModelScope.launch {
            try {
                siteApi.trackClick(api, key)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Not counted, and that is all.
            }
        }
    }
}

/**
 * These notes, then [page]'s that are not among them yet: paging by offset, a note sent in between pushes the others
 * down one, so the next page would begin with the last note already shown.
 */
private infix fun List<PublicNote>.appendNew(page: List<PublicNote>): List<PublicNote> {
    val ids = mapTo(HashSet()) { it.id }
    return this + page.filter { it.id !in ids }
}
