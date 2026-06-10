package com.kopilka.android.ui.category_detail

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kopilka.android.data.model.BudgetJson
import com.kopilka.android.data.model.SpendingEntryJson
import com.kopilka.android.data.storage.BudgetCache
import com.kopilka.android.data.sync.SyncManager
import com.kopilka.android.data.sync.SyncState
import com.kopilka.android.domain.RecentEntryUiState
import com.kopilka.android.domain.buildCategoryStates
import com.kopilka.android.domain.toComposeColor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

data class CategoryDetailUiState(
    val categoryId: String = "",
    val categoryName: String = "",
    val budgetAmount: Double = 0.0,
    val budgetPeriod: String = "",
    val spent: Double = 0.0,
    val progress: Float = 0f,
    val isOverBudget: Boolean = false,
    val entries: List<RecentEntryUiState> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

class CategoryDetailViewModel(app: Application) : AndroidViewModel(app) {

    private val sync = SyncManager(app)
    private val cache = BudgetCache(app)

    private val _state = MutableStateFlow(CategoryDetailUiState())
    val state: StateFlow<CategoryDetailUiState> = _state

    private var budget: BudgetJson? = null

    fun load(categoryId: String) {
        if (_state.value.categoryId == categoryId && !_state.value.isLoading) return
        _state.value = _state.value.copy(categoryId = categoryId, isLoading = true)
        viewModelScope.launch {
            val cached = cache.load()
            if (cached != null) {
                budget = cached
                applyBudget(cached, categoryId)
            } else {
                when (val r = sync.fetchBudget()) {
                    is SyncState.Ready -> {
                        budget = r.budget
                        applyBudget(r.budget, categoryId)
                    }
                    else -> _state.value = _state.value.copy(isLoading = false, error = "Could not load budget")
                }
            }
        }
    }

    private fun applyBudget(b: BudgetJson, categoryId: String) {
        val cat = b.categories.firstOrNull { it.id == categoryId }
            ?: run {
                _state.value = _state.value.copy(isLoading = false, error = "Category not found")
                return
            }
        val categoryStates = buildCategoryStates(b)
        val catState = categoryStates.firstOrNull { it.id == categoryId }
        val today = LocalDate.now()
        val catById = b.categories.associateBy { it.id }

        val periodEntries = b.spending
            .filter { it.categoryId == categoryId }
            .sortedByDescending { it.date }
            .map { entry ->
                val c = catById[entry.categoryId]
                RecentEntryUiState(
                    id = entry.id,
                    categoryName = c?.name ?: "Unknown",
                    categoryColor = c?.color?.toComposeColor(),
                    amount = entry.amount,
                    description = entry.description,
                    date = entry.date,
                    user = entry.user,
                )
            }

        _state.value = _state.value.copy(
            isLoading = false,
            error = null,
            categoryId = categoryId,
            categoryName = cat.name,
            budgetAmount = catState?.budgetAmount ?: cat.budgetAmount,
            budgetPeriod = cat.budgetPeriod,
            spent = catState?.spent ?: 0.0,
            progress = catState?.progress ?: 0f,
            isOverBudget = catState?.isOverBudget ?: false,
            entries = periodEntries,
        )
    }

    fun deleteEntry(entryId: String, myName: String) {
        viewModelScope.launch {
            val b = budget ?: return@launch
            val optimistic = b.copy(spending = b.spending.filter { it.id != entryId })
            budget = optimistic
            applyBudget(optimistic, _state.value.categoryId)
            sync.deleteEntry(entryId, myName)
        }
    }
}
