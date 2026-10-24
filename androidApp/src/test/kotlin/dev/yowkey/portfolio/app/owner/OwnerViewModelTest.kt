package dev.yowkey.portfolio.app.owner

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

    private fun owner(signedIn: Boolean = true) = OwnerViewModel(FakeAccess(worker, signedIn), FakeDevice())

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
