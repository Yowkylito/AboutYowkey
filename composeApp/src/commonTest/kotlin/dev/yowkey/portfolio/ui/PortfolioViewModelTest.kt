package dev.yowkey.portfolio.ui

import dev.yowkey.portfolio.data.PortfolioRepository
import dev.yowkey.portfolio.data.SiteApi
import dev.yowkey.portfolio.data.SiteApiException
import dev.yowkey.portfolio.model.FeedbackDraft
import dev.yowkey.portfolio.model.Link
import dev.yowkey.portfolio.model.MessageDraft
import dev.yowkey.portfolio.model.Portfolio
import dev.yowkey.portfolio.model.PublicNote
import dev.yowkey.portfolio.model.RatingSummary
import dev.yowkey.portfolio.model.SiteNotes
import dev.yowkey.portfolio.model.VisitCount
import dev.yowkey.portfolio.samplePortfolio
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class PortfolioViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private class FakeRepository(var result: () -> Portfolio) : PortfolioRepository {
        var calls = 0
        var gate: CompletableDeferred<Unit>? = null
        override suspend fun load(): Portfolio {
            calls++
            gate?.await()
            return result()
        }
    }

    private class FakeSiteApi : SiteApi {
        var visitResult: () -> VisitCount = { VisitCount(7, 1, "2026-10-24") }
        var notesResult: () -> SiteNotes = { SiteNotes(VisitCount(7), RatingSummary(1, 5.0), listOf(PublicNote(1, "Ada", 5, "Great", "2026-10-24"))) }
        var sendResult: () -> Unit = {}
        val visitCalls = mutableListOf<String>()
        val notesCalls = mutableListOf<String>()
        /** Each `loadNotes`'s star, `before` and `fresh`. */
        val notesQueries = mutableListOf<Triple<Int?, Long?, Boolean>>()
        val sent = mutableListOf<Pair<String, FeedbackDraft>>()
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun recordVisit(api: String): VisitCount {
            visitCalls += api
            return visitResult()
        }

        override suspend fun loadNotes(api: String, stars: Int?, before: Long?, fresh: Boolean): SiteNotes {
            notesCalls += api
            notesQueries += Triple(stars, before, fresh)
            return notesResult()
        }

        override suspend fun sendFeedback(api: String, draft: FeedbackDraft) {
            sent += api to draft
            gate?.await()
            sendResult()
        }

        var clickResult: () -> Unit = {}
        val clicks = mutableListOf<Pair<String, String>>()
        var messageResult: () -> Unit = {}
        val messages = mutableListOf<Pair<String, MessageDraft>>()
        var messageGate: CompletableDeferred<Unit>? = null

        override suspend fun trackClick(api: String, link: String) {
            clicks += api to link
            clickResult()
        }

        override suspend fun sendMessage(api: String, draft: MessageDraft) {
            messages += api to draft
            messageGate?.await()
            messageResult()
        }
    }

    private val withApi = samplePortfolio().copy(api = "https://api.example")

    @Test
    fun startsLoadingThenBecomesReady() = runTest(dispatcher) {
        val viewModel = PortfolioViewModel(FakeRepository { samplePortfolio() }, FakeSiteApi())
        assertEquals(PortfolioUiState.Loading, viewModel.state.value)
        advanceUntilIdle()
        assertEquals(PortfolioUiState.Ready(samplePortfolio()), viewModel.state.value)
    }

    @Test
    fun failureBecomesErrorAndRetryRecovers() = runTest(dispatcher) {
        val repository = FakeRepository { throw IllegalArgumentException("broken content") }
        val viewModel = PortfolioViewModel(repository, FakeSiteApi())
        advanceUntilIdle()
        assertEquals(PortfolioUiState.Error("broken content"), viewModel.state.value)

        repository.result = { samplePortfolio() }
        viewModel.retry()
        advanceUntilIdle()
        assertIs<PortfolioUiState.Ready>(viewModel.state.value)
    }

    @Test
    fun retryWhileLoadingDoesNotStartASecondLoad() = runTest(dispatcher) {
        val repository = FakeRepository { samplePortfolio() }.apply { gate = CompletableDeferred() }
        val viewModel = PortfolioViewModel(repository, FakeSiteApi())
        advanceUntilIdle()
        viewModel.retry()
        viewModel.retry()
        advanceUntilIdle()
        assertEquals(1, repository.calls)

        repository.gate?.complete(Unit)
        advanceUntilIdle()
        assertIs<PortfolioUiState.Ready>(viewModel.state.value)
    }

    @Test
    fun theVisitIsCountedOnceTheContentIsReadyAndOnlyWithABackend() = runTest(dispatcher) {
        val quiet = FakeSiteApi()
        PortfolioViewModel(FakeRepository { samplePortfolio() }, quiet).also { advanceUntilIdle() }
        assertEquals(emptyList(), quiet.visitCalls, "no api, no call")
        assertEquals(emptyList(), quiet.notesCalls)

        val siteApi = FakeSiteApi()
        val viewModel = PortfolioViewModel(FakeRepository { withApi }, siteApi)
        assertNull(viewModel.visits.value)
        assertNull(viewModel.notes.value)
        advanceUntilIdle()
        assertEquals(VisitCount(7, 1, "2026-10-24"), viewModel.visits.value)
        assertEquals(listOf("https://api.example"), siteApi.visitCalls)
        assertEquals(listOf("https://api.example"), siteApi.notesCalls)
        assertEquals(listOf("Ada"), viewModel.notes.value?.notes?.map { it.name })
    }

    @Test
    fun thePublishedNotesAreLoadedOnceAndAFailureLeavesNone() = runTest(dispatcher) {
        val repository = FakeRepository { throw IllegalStateException("first try") }
        val siteApi = FakeSiteApi()
        val viewModel = PortfolioViewModel(repository, siteApi)
        advanceUntilIdle()
        repository.result = { withApi }
        viewModel.retry()
        advanceUntilIdle()
        assertEquals(1, siteApi.notesCalls.size)

        val down = FakeSiteApi().apply { notesResult = { throw SiteApiException("No connection") } }
        val offline = PortfolioViewModel(FakeRepository { withApi }, down)
        advanceUntilIdle()
        assertIs<PortfolioUiState.Ready>(offline.state.value)
        assertNull(offline.notes.value)
        assertEquals(VisitCount(7, 1, "2026-10-24"), offline.visits.value, "the count does not depend on the notes")
    }

    @Test
    fun aStarPicksItsNotesMoreAddsTheNextPageAndANewNoteBringsAllBackFresh() = runTest(dispatcher) {
        val siteApi = FakeSiteApi()
        fun page(ids: List<Long>, more: Boolean) =
            SiteNotes(VisitCount(7), RatingSummary(3, 4.7), ids.map { PublicNote(it, null, 5, "M$it") }, total = 3, more = more)
        siteApi.notesResult = { page(listOf(3, 2), more = true) }
        val viewModel = PortfolioViewModel(FakeRepository { withApi }, siteApi)
        advanceUntilIdle()
        assertEquals(Triple(null, null, false), siteApi.notesQueries.single(), "the first page: all of them")

        viewModel.showNotes(5)
        assertEquals(NotesBrowse(5, loading = true), viewModel.notesBrowse.value)
        advanceUntilIdle()
        assertEquals(NotesBrowse(5), viewModel.notesBrowse.value)
        assertEquals(Triple(5, null, false), siteApi.notesQueries.last())
        viewModel.showNotes(5)
        advanceUntilIdle()
        assertEquals(2, siteApi.notesQueries.size, "the pick already showing is not asked for again")

        siteApi.notesResult = { page(listOf(1), more = false) }
        viewModel.showMoreNotes()
        advanceUntilIdle()
        assertEquals(Triple(5, 2L, false), siteApi.notesQueries.last(), "the page after the last note shown, same star")
        assertEquals(listOf(3L, 2L, 1L), viewModel.notes.value?.notes?.map { it.id })
        viewModel.showMoreNotes()
        advanceUntilIdle()
        assertEquals(3, siteApi.notesQueries.size, "nothing is left to load")

        viewModel.sendFeedback(FeedbackDraft(message = "Hi"))
        advanceUntilIdle()
        assertEquals(Triple(null, null, true), siteApi.notesQueries.last(), "a new note: all of them again, past the browser's copy")
        assertEquals(NotesBrowse(), viewModel.notesBrowse.value)
    }

    @Test
    fun theVisitIsCountedOnceWhateverTheRetriesAndAFailureLeavesNoCount() = runTest(dispatcher) {
        val repository = FakeRepository { throw IllegalStateException("first try") }
        val siteApi = FakeSiteApi()
        val viewModel = PortfolioViewModel(repository, siteApi)
        advanceUntilIdle()
        assertEquals(emptyList(), siteApi.visitCalls, "an error is not a visit")
        repository.result = { withApi }
        viewModel.retry()
        advanceUntilIdle()
        assertEquals(1, siteApi.visitCalls.size)

        val down = FakeSiteApi().apply { visitResult = { throw SiteApiException("No connection") } }
        val offline = PortfolioViewModel(FakeRepository { withApi }, down)
        advanceUntilIdle()
        assertIs<PortfolioUiState.Ready>(offline.state.value, "the page does not depend on the backend")
        assertNull(offline.visits.value)
    }

    @Test
    fun aNoteGoesOutOnceAndTheFormFollowsIt() = runTest(dispatcher) {
        val siteApi = FakeSiteApi().apply { gate = CompletableDeferred() }
        val viewModel = PortfolioViewModel(FakeRepository { withApi }, siteApi)
        advanceUntilIdle()
        assertEquals(FeedbackUiState.Idle, viewModel.feedback.value)

        val draft = FeedbackDraft("Ada", "", 5, "Hi")
        viewModel.sendFeedback(draft)
        advanceUntilIdle()
        assertEquals(FeedbackUiState.Sending, viewModel.feedback.value)
        viewModel.sendFeedback(draft.copy(message = "again"))
        viewModel.resetFeedback()
        advanceUntilIdle()
        assertEquals(FeedbackUiState.Sending, viewModel.feedback.value, "a second tap, or a reset, changes nothing mid-flight")
        assertEquals(listOf("https://api.example" to draft), siteApi.sent)

        siteApi.gate?.complete(Unit)
        advanceUntilIdle()
        assertEquals(FeedbackUiState.Sent, viewModel.feedback.value)
        viewModel.resetFeedback()
        assertEquals(FeedbackUiState.Idle, viewModel.feedback.value)
    }

    @Test
    fun aNoteWithAProblemOrARefusalFailsWithTheSentence() = runTest(dispatcher) {
        val siteApi = FakeSiteApi()
        val viewModel = PortfolioViewModel(FakeRepository { withApi }, siteApi)
        advanceUntilIdle()

        viewModel.sendFeedback(FeedbackDraft(message = "   "))
        advanceUntilIdle()
        assertEquals(FeedbackUiState.Failed("Write a message first."), viewModel.feedback.value)
        assertEquals(emptyList(), siteApi.sent, "nothing incomplete is sent")

        siteApi.sendResult = { throw SiteApiException("That is plenty for one day, thank you. Try again tomorrow.") }
        viewModel.sendFeedback(FeedbackDraft(message = "Sixth"))
        advanceUntilIdle()
        assertEquals(FeedbackUiState.Failed("That is plenty for one day, thank you. Try again tomorrow."), viewModel.feedback.value)
        assertEquals(1, siteApi.sent.size)
        viewModel.resetFeedback()
        assertEquals(FeedbackUiState.Idle, viewModel.feedback.value)
    }

    @Test
    fun withoutABackendNothingIsSent() = runTest(dispatcher) {
        val siteApi = FakeSiteApi()
        val viewModel = PortfolioViewModel(FakeRepository { samplePortfolio() }, siteApi)
        advanceUntilIdle()
        viewModel.sendFeedback(FeedbackDraft(message = "Hello?"))
        viewModel.sendMessage(MessageDraft(contact = "a@b.c", message = "Hello?"))
        viewModel.trackClick(Link("LinkedIn", "https://www.linkedin.com/in/ada"))
        advanceUntilIdle()
        assertEquals(FeedbackUiState.Idle, viewModel.feedback.value)
        assertEquals(MessageUiState.Idle, viewModel.message.value)
        assertEquals(emptyList(), siteApi.sent)
        assertEquals(emptyList(), siteApi.messages)
        assertEquals(emptyList(), siteApi.clicks)
    }

    private val linkedIn = Link("LinkedIn", "https://www.linkedin.com/in/ada")
    private val gitHub = Link("GitHub", "https://github.com/ada")

    @Test
    fun aMessageGoesOutOnceAndTheFormFollowsIt() = runTest(dispatcher) {
        val siteApi = FakeSiteApi().apply { messageGate = CompletableDeferred() }
        val viewModel = PortfolioViewModel(FakeRepository { withApi }, siteApi)
        advanceUntilIdle()
        assertEquals(MessageUiState.Idle, viewModel.message.value)

        val draft = MessageDraft("Ada", "ada@example.com", "job", "Are you open to a role?")
        viewModel.sendMessage(draft)
        advanceUntilIdle()
        assertEquals(MessageUiState.Sending, viewModel.message.value)
        viewModel.sendMessage(draft.copy(message = "again"))
        viewModel.resetMessage()
        advanceUntilIdle()
        assertEquals(MessageUiState.Sending, viewModel.message.value, "a second tap, or a reset, changes nothing mid-flight")
        assertEquals(listOf("https://api.example" to draft), siteApi.messages)
        assertEquals(FeedbackUiState.Idle, viewModel.feedback.value, "the note form is untouched")

        siteApi.messageGate?.complete(Unit)
        advanceUntilIdle()
        assertEquals(MessageUiState.Sent, viewModel.message.value)
        viewModel.resetMessage()
        assertEquals(MessageUiState.Idle, viewModel.message.value, "Send another")
    }

    @Test
    fun aMessageWithAProblemOrARefusalFailsWithTheSentence() = runTest(dispatcher) {
        val siteApi = FakeSiteApi()
        val viewModel = PortfolioViewModel(FakeRepository { withApi }, siteApi)
        advanceUntilIdle()

        viewModel.sendMessage(MessageDraft(message = "No way back"))
        advanceUntilIdle()
        assertEquals(MessageUiState.Failed("Leave an email or another way to reach you."), viewModel.message.value)
        assertEquals(emptyList(), siteApi.messages, "nothing incomplete is sent")

        siteApi.messageResult = { throw SiteApiException("That is plenty for one day, thank you. Try again tomorrow.") }
        viewModel.sendMessage(MessageDraft(contact = "a@b.c", message = "Fourth"))
        advanceUntilIdle()
        assertEquals(MessageUiState.Failed("That is plenty for one day, thank you. Try again tomorrow."), viewModel.message.value)
        assertEquals(1, siteApi.messages.size)
        viewModel.resetMessage()
        assertEquals(MessageUiState.Idle, viewModel.message.value)
    }

    @Test
    fun aTrackedLinkIsCountedOncePerLinkPerPageLoad() = runTest(dispatcher) {
        val siteApi = FakeSiteApi()
        val viewModel = PortfolioViewModel(FakeRepository { withApi }, siteApi)
        advanceUntilIdle()

        repeat(3) { viewModel.trackClick(linkedIn) }
        viewModel.trackClick(gitHub)
        viewModel.trackClick(gitHub.copy(url = "https://github.com/ada?tab=repositories"))
        advanceUntilIdle()
        assertEquals(listOf("https://api.example" to "linkedin", "https://api.example" to "github"), siteApi.clicks)
    }

    @Test
    fun anUntrackedLinkOrAClickBeforeTheContentIsCountsNothing() = runTest(dispatcher) {
        val repository = FakeRepository { withApi }.apply { gate = CompletableDeferred() }
        val siteApi = FakeSiteApi()
        val viewModel = PortfolioViewModel(repository, siteApi)
        viewModel.trackClick(linkedIn)
        advanceUntilIdle()
        assertEquals(emptyList(), siteApi.clicks, "no backend known yet")

        repository.gate?.complete(Unit)
        advanceUntilIdle()
        viewModel.trackClick(Link("Email me", "mailto:ada@example.com"))
        viewModel.trackClick(Link("Source on GitHub", "https://github.com/ada/site"))
        advanceUntilIdle()
        assertEquals(emptyList(), siteApi.clicks, "only the profile's LinkedIn and GitHub count")

        viewModel.trackClick(linkedIn)
        advanceUntilIdle()
        assertEquals(listOf("https://api.example" to "linkedin"), siteApi.clicks, "a click before the content does not use up the link")
    }

    @Test
    fun aFailedClickChangesNothingAndIsNotRetried() = runTest(dispatcher) {
        val siteApi = FakeSiteApi().apply { clickResult = { throw SiteApiException("No connection") } }
        val viewModel = PortfolioViewModel(FakeRepository { withApi }, siteApi)
        advanceUntilIdle()
        viewModel.trackClick(linkedIn)
        advanceUntilIdle()
        viewModel.trackClick(linkedIn)
        advanceUntilIdle()
        assertEquals(1, siteApi.clicks.size)
        assertIs<PortfolioUiState.Ready>(viewModel.state.value)
        assertEquals(MessageUiState.Idle, viewModel.message.value)
    }
}
