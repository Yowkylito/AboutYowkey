package dev.yowkey.portfolio.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.yowkey.portfolio.data.PortfolioRepository
import dev.yowkey.portfolio.model.Portfolio
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

class PortfolioViewModel(
    private val repository: PortfolioRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<PortfolioUiState>(PortfolioUiState.Loading)
    val state: StateFlow<PortfolioUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

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
                PortfolioUiState.Ready(repository.load())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                PortfolioUiState.Error(e.message ?: "The portfolio could not be loaded.")
            }
        }
    }
}
