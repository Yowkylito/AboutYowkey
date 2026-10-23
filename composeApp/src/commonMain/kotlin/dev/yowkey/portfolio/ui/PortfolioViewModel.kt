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
import dev.yowkey.portfolio.model.SiteNotes
import dev.yowkey.portfolio.model.VisitCount
import dev.yowkey.portfolio.model.trackingKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
 * How the visitor is browsing the notes: all of them, or only [stars]'s (the dashboard's bar they picked), and
 * whether a page of them is on its way.
 */
data class NotesBrowse(val stars: Int? = null, val loading: Boolean = false)

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
     * The rating and the notes shown so far (every page loaded, of the picked star) once the backend has answered;
     * null without a backend, or until it does.
     */
    private val _notes = MutableStateFlow<SiteNotes?>(null)
    val notes: StateFlow<SiteNotes?> = _notes.asStateFlow()

    private val _notesBrowse = MutableStateFlow(NotesBrowse())
    val notesBrowse: StateFlow<NotesBrowse> = _notesBrowse.asStateFlow()

    private val _feedback = MutableStateFlow<FeedbackUiState>(FeedbackUiState.Idle)
    val feedback: StateFlow<FeedbackUiState> = _feedback.asStateFlow()

    private val _message = MutableStateFlow<MessageUiState>(MessageUiState.Idle)
    val message: StateFlow<MessageUiState> = _message.asStateFlow()

    private var loadJob: Job? = null
    private var visitJob: Job? = null
    private var notesJob: Job? = null
    private var pageJob: Job? = null
    private var sendJob: Job? = null
    private var messageJob: Job? = null

    /** The tracked links already counted on this page load, by their `trackingKey`. */
    private val clicked = mutableSetOf<String>()

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
     * Once per page load as well; without an answer the page simply has no notes and no rating. A pick the visitor
     * made before it answered wins over it.
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

    /**
     * Shows only the notes with [stars] stars, or all of them for null, from the newest; the rating stays the whole
     * site's. A pick already showing is left as it is; a page still on its way for another pick is dropped.
     */
    fun showNotes(stars: Int?) {
        if (stars == _notesBrowse.value.stars && pageJob?.isActive != true) return
        loadPage(stars, before = null)
    }

    /** The next page of the notes showing, after the last one; nothing while a page is on its way or none is left. */
    fun showMoreNotes() {
        val shown = _notes.value ?: return
        if (!shown.more || pageJob?.isActive == true) return
        loadPage(_notesBrowse.value.stars, before = shown.notes.lastOrNull()?.id)
    }

    /**
     * A page of notes: the first of [stars]'s (replacing the list), or the one after [before] (added to it). [fresh]
     * asks past the browser's copy, for a list the visitor has just added to. A failure leaves the list as it was.
     */
    private fun loadPage(stars: Int?, before: Long?, fresh: Boolean = false) {
        val api = (state.value as? PortfolioUiState.Ready)?.portfolio?.api ?: return
        pageJob?.cancel()
        _notesBrowse.value = NotesBrowse(stars, loading = true)
        pageJob = viewModelScope.launch {
            try {
                val page = siteApi.loadNotes(api, stars, before, fresh)
                val shown = _notes.value
                _notes.value = if (before == null || shown == null) page else page.copy(notes = shown.notes + page.notes)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The list stays as it was; the visitor can pick again.
            }
            _notesBrowse.value = NotesBrowse(stars, loading = false)
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
                // The note is public at once: back to all the notes, newest first, past the browser's copy.
                loadPage(stars = null, before = null, fresh = true)
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
