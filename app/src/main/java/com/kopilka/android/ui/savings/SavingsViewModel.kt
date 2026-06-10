package com.kopilka.android.ui.savings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kopilka.android.data.model.SavingsGoalJson
import com.kopilka.android.data.storage.BudgetCache
import com.kopilka.android.data.sync.SyncManager
import com.kopilka.android.data.sync.SyncState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class SavingsUiState(
    val goals: List<SavingsGoalJson> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

class SavingsViewModel(app: Application) : AndroidViewModel(app) {

    private val sync = SyncManager(app)
    private val cache = BudgetCache(app)

    private val _state = MutableStateFlow(SavingsUiState())
    val state: StateFlow<SavingsUiState> = _state

    init { load() }

    private fun load() {
        viewModelScope.launch {
            val cached = cache.load()
            if (cached != null) {
                _state.value = SavingsUiState(goals = cached.savingsGoals, isLoading = false)
            } else {
                when (val r = sync.fetchBudget()) {
                    is SyncState.Ready -> _state.value = SavingsUiState(goals = r.budget.savingsGoals, isLoading = false)
                    else -> _state.value = SavingsUiState(isLoading = false, error = "Could not load budget")
                }
            }
        }
    }
}
