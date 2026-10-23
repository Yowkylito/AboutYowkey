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

    /** The published notes and their rating once the backend has answered; null without a backend, or until it does. */
    private val _notes = MutableStateFlow<SiteNotes?>(null)
    val notes: StateFlow<SiteNotes?> = _notes.asStateFlow()

    private val _feedback = MutableStateFlow<FeedbackUiState>(FeedbackUiState.Idle)
    val feedback: StateFlow<FeedbackUiState> = _feedback.asStateFlow()

    private val _message = MutableStateFlow<MessageUiState>(MessageUiState.Idle)
    val message: StateFlow<MessageUiState> = _message.asStateFlow()

    private var loadJob: Job? = null
    private var visitJob: Job? = null
    private var notesJob: Job? = null
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

    /** Once per page load as well; without an answer the page simply has no notes and no rating. */
    private fun loadNotes(api: String?) {
        if (api == null || notesJob != null) return
        notesJob = viewModelScope.launch {
            _notes.value = try {
                siteApi.loadNotes(api)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
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
