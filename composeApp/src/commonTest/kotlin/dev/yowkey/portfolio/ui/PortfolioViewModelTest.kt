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
import dev.yowkey.portfolio.model.Relation
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
import kotlin.test.assertTrue

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

    /** What one `loadNotes` asked for. */
    private data class NotesQuery(val stars: Int? = null, val relation: String? = null, val offset: Int = 0, val fresh: Boolean = false)

    private class FakeSiteApi : SiteApi {
        var visitResult: () -> VisitCount = { VisitCount(7, 1, "2026-10-24") }
        var notesResult: (NotesQuery) -> SiteNotes = { SiteNotes(VisitCount(7), RatingSummary(1, 5.0), listOf(PublicNote(1, "Ada", 5, "Great", "2026-10-24"))) }
        var sendResult: () -> Unit = {}
        val visitCalls = mutableListOf<String>()
        val notesCalls = mutableListOf<String>()
        val notesQueries = mutableListOf<NotesQuery>()
        val sent = mutableListOf<Pair<String, FeedbackDraft>>()
        var gate: CompletableDeferred<Unit>? = null
        /** Holds every `loadNotes` until completed, for an answer that comes late. */
        var notesGate: CompletableDeferred<Unit>? = null

        override suspend fun recordVisit(api: String): VisitCount {
            visitCalls += api
            return visitResult()
        }

        override suspend fun loadNotes(api: String, stars: Int?, relation: String?, offset: Int, fresh: Boolean): SiteNotes {
            notesCalls += api
            val query = NotesQuery(stars, relation, offset, fresh)
            notesQueries += query
            notesGate?.await()
            return notesResult(query)
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

    private fun page(ids: List<Long>, more: Boolean = false, relations: Map<String, Int> = emptyMap()) =
        SiteNotes(VisitCount(7), RatingSummary(3, 4.7), ids.map { PublicNote(it, null, 5, "M$it") }, total = ids.size, more = more, relations = relations)

    @Test
    fun moreAddsTheNextPageByOffsetAndANewNoteBringsTheFirstBackFresh() = runTest(dispatcher) {
        val siteApi = FakeSiteApi()
        siteApi.notesResult = { page(listOf(3, 2), more = true) }
        val viewModel = PortfolioViewModel(FakeRepository { withApi }, siteApi)
        advanceUntilIdle()
        assertEquals(NotesQuery(), siteApi.notesQueries.single(), "the first page: all of them, pinned first as the backend orders them")

        // A note sent in between pushes the others down: the page 2 in begins with the last one shown.
        siteApi.notesResult = { page(listOf(2, 1), more = false) }
        viewModel.showMoreNotes()
        assertEquals(true, viewModel.notesLoading.value)
        advanceUntilIdle()
        assertEquals(false, viewModel.notesLoading.value)
        assertEquals(NotesQuery(offset = 2), siteApi.notesQueries.last(), "the page as many notes in as are shown")
        assertEquals(listOf(3L, 2L, 1L), viewModel.notes.value?.notes?.map { it.id }, "and none twice")
        viewModel.showMoreNotes()
        advanceUntilIdle()
        assertEquals(2, siteApi.notesQueries.size, "nothing is left to load")

        siteApi.notesResult = { page(listOf(4, 3), more = true) }
        viewModel.sendFeedback(FeedbackDraft(message = "Hi"))
        advanceUntilIdle()
        assertEquals(NotesQuery(fresh = true), siteApi.notesQueries.last(), "a new note: the first page again, past the browser's copy")
        assertEquals(listOf(4L, 3L), viewModel.notes.value?.notes?.map { it.id })
        viewModel.showMoreNotes()
        advanceUntilIdle()
        assertEquals(NotesQuery(offset = 2, fresh = true), siteApi.notesQueries.last(), "and every page after it")
    }

    @Test
    fun aFailedPageLeavesTheListAsItWas() = runTest(dispatcher) {
        val siteApi = FakeSiteApi().apply { notesResult = { page(listOf(3, 2), more = true) } }
        val viewModel = PortfolioViewModel(FakeRepository { withApi }, siteApi)
        advanceUntilIdle()
        siteApi.notesResult = { throw SiteApiException("No connection") }
        viewModel.showMoreNotes()
        advanceUntilIdle()
        assertEquals(listOf(3L, 2L), viewModel.notes.value?.notes?.map { it.id })
        assertEquals(false, viewModel.notesLoading.value)
    }

    @Test
    fun aBarOpensItsSheetWhichSortsByRelationAndPagesOnItsOwn() = runTest(dispatcher) {
        val siteApi = FakeSiteApi()
        val counts = mapOf("colleague" to 3, "friend" to 0, "special" to 0, "other" to 1)
        val viewModel = PortfolioViewModel(FakeRepository { withApi }, siteApi)
        advanceUntilIdle()
        val main = viewModel.notes.value
        assertNull(viewModel.sheet.value, "no sheet until a bar is tapped")

        siteApi.notesResult = { page(listOf(9, 8), more = true, relations = counts) }
        viewModel.openSheet(5)
        assertEquals(StarSheet(5, loading = true), viewModel.sheet.value)
        advanceUntilIdle()
        assertEquals(NotesQuery(stars = 5), siteApi.notesQueries.last(), "the star's first page, every relation")
        assertEquals(StarSheet(5, page = page(listOf(9, 8), more = true, relations = counts)), viewModel.sheet.value)
        assertEquals(4, viewModel.sheet.value?.readable)

        siteApi.notesResult = { page(listOf(9, 7), more = true, relations = counts) }
        viewModel.pickRelation(Relation.Colleague)
        assertEquals(Relation.Colleague, viewModel.sheet.value?.relation)
        assertEquals(listOf(9L, 8L), viewModel.sheet.value?.page?.notes?.map { it.id }, "the last pick's page stays while the new one loads")
        assertEquals(true, viewModel.sheet.value?.loading)
        advanceUntilIdle()
        assertEquals(NotesQuery(stars = 5, relation = "colleague"), siteApi.notesQueries.last())
        assertEquals(listOf(9L, 7L), viewModel.sheet.value?.page?.notes?.map { it.id })
        assertEquals(false, viewModel.sheet.value?.loading)
        viewModel.pickRelation(Relation.Colleague)
        advanceUntilIdle()
        assertEquals(3, siteApi.notesQueries.size, "the pick already showing is not asked for again")

        siteApi.notesResult = { page(listOf(7, 6), more = false, relations = counts) }
        viewModel.showMoreInSheet()
        assertEquals(true, viewModel.sheet.value?.loadingMore)
        advanceUntilIdle()
        assertEquals(NotesQuery(stars = 5, relation = "colleague", offset = 2), siteApi.notesQueries.last(), "the pick's next page")
        assertEquals(listOf(9L, 7L, 6L), viewModel.sheet.value?.page?.notes?.map { it.id }, "and none twice")
        assertEquals(false, viewModel.sheet.value?.loadingMore)
        viewModel.showMoreInSheet()
        advanceUntilIdle()
        assertEquals(4, siteApi.notesQueries.size, "nothing is left to load")

        viewModel.pickRelation(null)
        advanceUntilIdle()
        assertEquals(NotesQuery(stars = 5), siteApi.notesQueries.last(), "All again")
        assertEquals(main, viewModel.notes.value, "the list under the dashboard is untouched by the sheet")
        viewModel.closeSheet()
        assertNull(viewModel.sheet.value)
    }

    @Test
    fun aPageForASheetSinceClosedOrAPickSinceChangedIsDropped() = runTest(dispatcher) {
        val siteApi = FakeSiteApi()
        val viewModel = PortfolioViewModel(FakeRepository { withApi }, siteApi)
        advanceUntilIdle()

        siteApi.notesGate = CompletableDeferred()
        viewModel.openSheet(4)
        advanceUntilIdle()
        viewModel.closeSheet()
        siteApi.notesGate?.complete(Unit)
        advanceUntilIdle()
        assertNull(viewModel.sheet.value, "a late answer does not open it again")

        siteApi.notesGate = null
        siteApi.notesResult = { query -> page(listOf(if (query.relation == "friend") 2 else 1)) }
        viewModel.openSheet(4)
        advanceUntilIdle()
        siteApi.notesGate = CompletableDeferred()
        viewModel.pickRelation(Relation.Friend)
        advanceUntilIdle()
        viewModel.pickRelation(Relation.Other)
        siteApi.notesGate?.complete(Unit)
        advanceUntilIdle()
        assertEquals(Relation.Other, viewModel.sheet.value?.relation)
        assertEquals(listOf(1L), viewModel.sheet.value?.page?.notes?.map { it.id }, "the friends' page, asked for before, is dropped")
    }

    @Test
    fun aFailedPickGoesBackToThePickShowingAndAFailedFirstPageCanBeAskedAgain() = runTest(dispatcher) {
        val siteApi = FakeSiteApi()
        val viewModel = PortfolioViewModel(FakeRepository { withApi }, siteApi)
        advanceUntilIdle()

        siteApi.notesResult = { throw SiteApiException("No connection") }
        viewModel.openSheet(5)
        advanceUntilIdle()
        assertEquals(StarSheet(5), viewModel.sheet.value, "open, with nothing to show and nothing on its way")

        siteApi.notesResult = { page(listOf(3)) }
        viewModel.pickRelation(null)
        advanceUntilIdle()
        assertEquals(NotesQuery(stars = 5), siteApi.notesQueries.last(), "the same pick, asked again")
        assertEquals(listOf(3L), viewModel.sheet.value?.page?.notes?.map { it.id })

        siteApi.notesResult = { throw SiteApiException("No connection") }
        viewModel.pickRelation(Relation.Friend)
        viewModel.pickRelation(Relation.Special)
        advanceUntilIdle()
        assertEquals(StarSheet(5, relation = null, page = page(listOf(3))), viewModel.sheet.value, "back to the pick its page is for")
    }

    @Test
    fun aSentNoteReloadsTheOpenSheetFreshAndEverySheetAfter() = runTest(dispatcher) {
        val siteApi = FakeSiteApi()
        val viewModel = PortfolioViewModel(FakeRepository { withApi }, siteApi)
        advanceUntilIdle()
        viewModel.openSheet(5)
        viewModel.pickRelation(Relation.Friend)
        advanceUntilIdle()

        viewModel.sendFeedback(FeedbackDraft(message = "Hi", rating = 5, relation = "friend"))
        advanceUntilIdle()
        assertTrue(NotesQuery(fresh = true) in siteApi.notesQueries, "the list under the dashboard")
        assertEquals(NotesQuery(stars = 5, relation = "friend", fresh = true), siteApi.notesQueries.last(), "and the sheet, as it is sorted")
        assertEquals(false, viewModel.sheet.value?.loading)

        viewModel.closeSheet()
        viewModel.openSheet(3)
        advanceUntilIdle()
        assertEquals(NotesQuery(stars = 3, fresh = true), siteApi.notesQueries.last(), "a sheet opened later has the note too")
    }

    @Test
    fun noSheetWithoutABackendOrForAStarThatHasNoBar() = runTest(dispatcher) {
        val quiet = FakeSiteApi()
        val offline = PortfolioViewModel(FakeRepository { samplePortfolio() }, quiet)
        advanceUntilIdle()
        offline.openSheet(5)
        offline.pickRelation(Relation.Friend)
        offline.showMoreInSheet()
        advanceUntilIdle()
        assertNull(offline.sheet.value)
        assertEquals(emptyList(), quiet.notesQueries)

        val siteApi = FakeSiteApi()
        val viewModel = PortfolioViewModel(FakeRepository { withApi }, siteApi)
        advanceUntilIdle()
        viewModel.openSheet(0)
        viewModel.openSheet(6)
        advanceUntilIdle()
        assertNull(viewModel.sheet.value)
        assertEquals(1, siteApi.notesQueries.size, "only the page load's")
    }

    /** The sorter's counts: All's is every relation's together, a reply without counts falls back on its total. */
    @Test
    fun aSheetCountsItsNotesByRelation() {
        val sheet = StarSheet(5, page = page(listOf(1, 2), relations = mapOf("colleague" to 1, "friend" to 0, "special" to 2, "other" to 3)))
        assertEquals(6, sheet.readable)
        assertEquals(listOf(6, 1, 0, 2, 3), (listOf(null) + Relation.entries).map(sheet::countOf))
        assertEquals(2, StarSheet(5, page = page(listOf(1, 2))).readable)
        assertEquals(0, StarSheet(5).readable, "nothing known yet")
        assertEquals(0, StarSheet(5).countOf(Relation.Friend))
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
