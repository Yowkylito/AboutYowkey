package dev.yowkey.portfolio.ui

import dev.yowkey.portfolio.data.PortfolioRepository
import dev.yowkey.portfolio.data.SiteApi
import dev.yowkey.portfolio.data.SiteApiException
import dev.yowkey.portfolio.model.FeedbackDraft
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
        val sent = mutableListOf<Pair<String, FeedbackDraft>>()
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun recordVisit(api: String): VisitCount {
            visitCalls += api
            return visitResult()
        }

        override suspend fun loadNotes(api: String): SiteNotes {
            notesCalls += api
            return notesResult()
        }

        override suspend fun sendFeedback(api: String, draft: FeedbackDraft) {
            sent += api to draft
            gate?.await()
            sendResult()
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
        advanceUntilIdle()
        assertEquals(FeedbackUiState.Idle, viewModel.feedback.value)
        assertEquals(emptyList(), siteApi.sent)
    }
}
