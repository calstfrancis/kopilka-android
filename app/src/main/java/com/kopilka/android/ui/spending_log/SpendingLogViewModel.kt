package com.kopilka.android.ui.spending_log

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kopilka.android.data.model.BudgetJson
import com.kopilka.android.data.storage.BudgetCache
import com.kopilka.android.data.sync.SyncManager
import com.kopilka.android.data.sync.SyncState
import com.kopilka.android.domain.RecentEntryUiState
import com.kopilka.android.domain.buildRecentEntries
import com.kopilka.android.domain.toComposeColor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

enum class LogPeriod(val label: String) {
    WEEK("This week"),
    MONTH("This month"),
    ALL("All time"),
}

data class SpendingLogUiState(
    val entries: List<RecentEntryUiState> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val period: LogPeriod = LogPeriod.MONTH,
    val searchQuery: String = "",
    /** Monthly totals for last 6 months, index 0 = oldest. */
    val monthlyTotals: List<Pair<String, Float>> = emptyList(),
)

class SpendingLogViewModel(app: Application) : AndroidViewModel(app) {

    private val sync = SyncManager(app)
    private val cache = BudgetCache(app)

    private val _state = MutableStateFlow(SpendingLogUiState())
    val state: StateFlow<SpendingLogUiState> = _state

    private var budget: BudgetJson? = null

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            val cached = cache.load()
            if (cached != null) {
                budget = cached
                applyFilter()
            } else {
                when (val r = sync.fetchBudget()) {
                    is SyncState.Ready -> {
                        budget = r.budget
                        applyFilter()
                    }
                    else -> _state.value = _state.value.copy(
                        isLoading = false,
                        error = "Could not load budget",
                    )
                }
            }
        }
    }

    fun setPeriod(period: LogPeriod) {
        _state.value = _state.value.copy(period = period)
        applyFilter()
    }

    fun setSearch(q: String) {
        _state.value = _state.value.copy(searchQuery = q)
        applyFilter()
    }

    fun deleteEntry(entryId: String, myName: String) {
        viewModelScope.launch {
            val b = budget ?: return@launch
            val optimistic = b.copy(spending = b.spending.filter { it.id != entryId })
            budget = optimistic
            applyFilter()
            sync.deleteEntry(entryId, myName)
        }
    }

    private fun applyFilter() {
        val b = budget ?: return
        val today = LocalDate.now()
        val catById = b.categories.associateBy { it.id }
        val query = _state.value.searchQuery.trim().lowercase()

        val filtered = b.spending
            .filter { entry ->
                val inPeriod = when (_state.value.period) {
                    LogPeriod.WEEK -> {
                        val d = LocalDate.parse(entry.date)
                        d >= today.minusDays(6)
                    }
                    LogPeriod.MONTH -> {
                        val d = LocalDate.parse(entry.date)
                        d.year == today.year && d.monthValue == today.monthValue
                    }
                    LogPeriod.ALL -> true
                }
                if (!inPeriod) return@filter false
                if (query.isEmpty()) return@filter true
                val catName = catById[entry.categoryId]?.name ?: ""
                entry.description.lowercase().contains(query) || catName.lowercase().contains(query)
            }
            .sortedByDescending { it.date }
            .map { entry ->
                val cat = catById[entry.categoryId]
                RecentEntryUiState(
                    id = entry.id,
                    categoryName = cat?.name ?: if (entry.categoryId == "__one_time__") "One-time" else "Unknown",
                    categoryColor = cat?.color?.toComposeColor(),
                    amount = entry.amount,
                    description = entry.description,
                    date = entry.date,
                    user = entry.user,
                )
            }

        // Build 6-month bar chart data
        val monthFmt = DateTimeFormatter.ofPattern("MMM")
        val monthlyTotals = (5 downTo 0).map { monthsAgo ->
            val month = today.minusMonths(monthsAgo.toLong())
            val label = month.format(monthFmt)
            val total = b.spending
                .filter { entry ->
                    try {
                        val d = LocalDate.parse(entry.date)
                        d.year == month.year && d.monthValue == month.monthValue
                    } catch (_: Exception) { false }
                }
                .sumOf { it.amount }
                .toFloat()
            label to total
        }

        _state.value = _state.value.copy(
            isLoading = false,
            entries = filtered,
            error = null,
            monthlyTotals = monthlyTotals,
        )
    }
}
