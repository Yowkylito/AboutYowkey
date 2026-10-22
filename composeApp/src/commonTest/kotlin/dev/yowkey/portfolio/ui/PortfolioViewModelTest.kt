package dev.yowkey.portfolio.ui

import dev.yowkey.portfolio.data.PortfolioRepository
import dev.yowkey.portfolio.model.Portfolio
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

    @Test
    fun startsLoadingThenBecomesReady() = runTest(dispatcher) {
        val viewModel = PortfolioViewModel(FakeRepository { samplePortfolio() })
        assertEquals(PortfolioUiState.Loading, viewModel.state.value)
        advanceUntilIdle()
        assertEquals(PortfolioUiState.Ready(samplePortfolio()), viewModel.state.value)
    }

    @Test
    fun failureBecomesErrorAndRetryRecovers() = runTest(dispatcher) {
        val repository = FakeRepository { throw IllegalArgumentException("broken content") }
        val viewModel = PortfolioViewModel(repository)
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
        val viewModel = PortfolioViewModel(repository)
        advanceUntilIdle()
        viewModel.retry()
        viewModel.retry()
        advanceUntilIdle()
        assertEquals(1, repository.calls)

        repository.gate?.complete(Unit)
        advanceUntilIdle()
        assertIs<PortfolioUiState.Ready>(viewModel.state.value)
    }
}
