package com.kopilka.android.ui.debt

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kopilka.android.data.model.DebtJson
import com.kopilka.android.data.storage.BudgetCache
import com.kopilka.android.data.sync.SyncManager
import com.kopilka.android.data.sync.SyncState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class DebtUiState(
    val debts: List<DebtJson> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

class DebtViewModel(app: Application) : AndroidViewModel(app) {

    private val sync = SyncManager(app)
    private val cache = BudgetCache(app)

    private val _state = MutableStateFlow(DebtUiState())
    val state: StateFlow<DebtUiState> = _state

    init { load() }

    private fun load() {
        viewModelScope.launch {
            val cached = cache.load()
            if (cached != null) {
                _state.value = DebtUiState(debts = cached.debt, isLoading = false)
            } else {
                when (val r = sync.fetchBudget()) {
                    is SyncState.Ready -> _state.value = DebtUiState(debts = r.budget.debt, isLoading = false)
                    else -> _state.value = DebtUiState(isLoading = false, error = "Could not load budget")
                }
            }
        }
    }
}
