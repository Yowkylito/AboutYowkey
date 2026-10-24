package dev.yowkey.portfolio.app.owner

import androidx.lifecycle.SavedStateHandle
import dev.yowkey.portfolio.admin.MessageFilter
import dev.yowkey.portfolio.app.OwnerLink
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

    private fun owner(signedIn: Boolean = true, saved: SavedStateHandle = SavedStateHandle()) =
        OwnerViewModel(FakeAccess(worker, signedIn), FakeDevice(), saved)

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
