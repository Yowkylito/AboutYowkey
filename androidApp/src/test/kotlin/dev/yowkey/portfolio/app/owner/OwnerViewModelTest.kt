package dev.yowkey.portfolio.app.owner

import androidx.lifecycle.SavedStateHandle
import dev.yowkey.portfolio.admin.MessageFilter
import dev.yowkey.portfolio.app.OwnerLink
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** The admin view's rules, against a fake worker: where the app is, what it loads, and when. */
@OptIn(ExperimentalCoroutinesApi::class)
class OwnerViewModelTest {

    private val main = UnconfinedTestDispatcher()
    private val worker = FakeWorker()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(main)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    /** The view model over the fake worker; [main] stands in for the IO pool too, so every step runs at once. */
    private fun owner(signedIn: Boolean = true, saved: SavedStateHandle = SavedStateHandle()) =
        OwnerViewModel(FakeAccess(worker, signedIn), FakeDevice(), saved, io = main)

    /**
     * The process is killed in the background: the activity's saved state outlives it (as a Bundle, so only what was
     * put there), and MainActivity, given that state, does not call [OwnerViewModel.start] again.
     */
    private fun afterProcessDeath(saved: SavedStateHandle): SavedStateHandle =
        SavedStateHandle(saved.keys().associateWith { saved.get<Any?>(it) })

    @Test
    fun theAdminViewComesBackWhereItWasAfterTheProcessIsKilled() = runTest(main) {
        val saved = SavedStateHandle()
        val before = owner(saved = saved)
        before.start(OwnerLink.Messages(1))
        before.setFilter(MessageFilter.Hiring)
        worker.requests.clear()

        val after = owner(saved = afterProcessDeath(saved))
        assertEquals(OwnerNav(portfolio = false, tab = Tab.Messages, messageId = 1), after.nav.value)
        assertEquals(MessageFilter.Hiring, after.filter.value)
        assertEquals(listOf("GET /admin/messages?limit=100"), worker.requests.filter { it.startsWith("GET") }, "and the tab's data loads again")
        assertNotNull(after.messages.value.value)
    }

    @Test
    fun aReloadAskedForWhileOneIsUnderwayRunsAfterIt() = runTest(main) {
        val owner = owner()
        val gate = CompletableDeferred<Unit>().also { worker.summaryGate = it }
        owner.start(link = null) // the dashboard's summary is read (1 unread) and held back
        owner.select(Tab.Messages)
        owner.openMessage(1) // marks it read, then asks for the dashboard again while the first load is underway
        assertEquals(0, worker.unread)
        gate.complete(Unit)
        assertEquals(2, worker.requests.count { it == "GET /admin/summary" }, "read again after the change")
        assertEquals(0, owner.dashboard.value.value?.summary?.messages?.unread, "the badge shows the message as read")
        assertFalse(owner.dashboard.value.loading)
    }

    @Test
    fun anActionReloadsTheSummaryAloneNotTheThirtyDays() = runTest(main) {
        val owner = owner()
        owner.start(link = null)
        owner.select(Tab.Messages)
        worker.requests.clear()
        owner.openMessage(1)
        assertEquals(listOf("POST /admin/messages/1", "GET /admin/summary"), worker.requests)
        assertEquals(0, owner.dashboard.value.value?.summary?.messages?.unread)
    }

    @Test
    fun anActionBeforeTheDashboardEverLoadedLoadsItWhole() = runTest(main) {
        val owner = owner()
        owner.start(OwnerLink.Messages(1)) // a notification's message: the dashboard has not loaded yet
        assertEquals(
            listOf("GET /admin/messages?limit=100", "POST /admin/messages/1", "GET /admin/summary", "GET /admin/days?limit=30"),
            worker.requests,
        )
    }

    @Test
    fun theDashboardTabAskedForDuringASummaryReloadStillGetsTheThirtyDays() = runTest(main) {
        val owner = owner()
        owner.start(link = null)
        owner.select(Tab.Messages)
        val gate = CompletableDeferred<Unit>().also { worker.summaryGate = it }
        owner.openMessage(1) // the summary alone, held back
        owner.select(Tab.Dashboard) // asks for the whole dashboard meanwhile
        worker.requests.clear()
        gate.complete(Unit)
        assertEquals(listOf("GET /admin/summary", "GET /admin/days?limit=30"), worker.requests)
    }

    @Test
    fun theKeystoreAndTheJsonStayOffTheMainThread() = runTest(main) {
        val io = UnconfinedTestDispatcher(testScheduler, name = "io")
        val access = FakeAccess(worker, signedIn = false)
        val owner = OwnerViewModel(access, FakeDevice(), SavedStateHandle(), io)
        owner.start(OwnerLink.Dashboard)
        owner.signIn("test-password") // seals the token, then loads the dashboard
        owner.select(Tab.Messages)
        owner.openMessage(1) // an action
        owner.signOut()
        assertEquals(6, access.tokenDispatchers.size, "sign-in, the dashboard, the messages, the action, its reload, sign-out")
        assertEquals(listOf<Any?>(io), access.tokenDispatchers.distinct(), "every call that opens the token")
        assertEquals(listOf<Any?>(io), worker.dispatchers.distinct(), "every request, and so the decoding of its answer")
    }

    @Test
    fun aNotificationsLinkLoadsItsTabOnce() = runTest(main) {
        val owner = owner()
        owner.start(OwnerLink.Notes(5))
        assertEquals(listOf("GET /admin/feedback?limit=100"), worker.requests)
    }

    @Test
    fun aLoadUnderwayWhenTheOwnerSignsOutLeavesNothingBehind() = runTest(main) {
        val owner = owner()
        val gate = CompletableDeferred<Unit>().also { worker.summaryGate = it }
        owner.start(link = null)
        owner.signOut()
        gate.complete(Unit)
        assertEquals(Loadable(), owner.dashboard.value, "the signed-out owner's figures are not put back")
    }

    @Test
    fun aFreshStartWithNoSavedStateIsTheDefault() = runTest(main) {
        val owner = owner()
        assertEquals(OwnerNav(), owner.nav.value)
        assertEquals(emptyList(), worker.requests, "nothing loads before start()")
    }

    @Test
    fun signedInTheAppOpensOnTheDashboardAndLoadsIt() = runTest(main) {
        val owner = owner()
        owner.start(link = null)
        assertEquals(OwnerNav(portfolio = false), owner.nav.value)
        assertEquals(1, owner.dashboard.value.value?.summary?.messages?.unread)
    }

    @Test
    fun signedOutTheAppOpensOnThePortfolioAndAsksTheWorkerNothing() = runTest(main) {
        val owner = owner(signedIn = false)
        owner.start(link = null)
        assertTrue(owner.nav.value.portfolio)
        assertEquals(emptyList(), worker.requests)
    }

    @Test
    fun backClosesTheMessageThenGoesToTheDashboardThenLeaves() = runTest(main) {
        val owner = owner()
        owner.start(OwnerLink.Messages(1))
        assertEquals(MessageFilter.All, owner.filter.value)
        assertTrue(owner.back(), "closes the message")
        assertEquals(OwnerNav(portfolio = false, tab = Tab.Messages), owner.nav.value)
        assertTrue(owner.back(), "goes to the dashboard")
        assertEquals(Tab.Dashboard, owner.nav.value.tab)
        assertFalse(owner.back(), "leaves")
    }
}
