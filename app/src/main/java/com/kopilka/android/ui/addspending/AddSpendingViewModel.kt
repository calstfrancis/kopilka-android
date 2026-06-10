package com.kopilka.android.ui.addspending

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kopilka.android.data.model.BudgetJson
import com.kopilka.android.data.model.SpendingEntryJson
import com.kopilka.android.data.storage.BudgetCache
import com.kopilka.android.data.storage.CredentialStore
import com.kopilka.android.data.sync.SyncManager
import com.kopilka.android.data.sync.SyncState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

data class AddSpendingUiState(
    val budget: BudgetJson? = null,
    val isLoadingBudget: Boolean = true,
    val selectedCategoryId: String = "",
    val amount: String = "",
    val description: String = "",
    val date: LocalDate = LocalDate.now(),
    val selectedUser: String = "",
    val coupleNames: List<String> = emptyList(),
    val isSaving: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false,
    val editingEntryId: String? = null,
)

class AddSpendingViewModel(app: Application) : AndroidViewModel(app) {

    private val sync = SyncManager(app)
    private val creds = CredentialStore(app)
    private val cache = BudgetCache(app)

    private val _state = MutableStateFlow(AddSpendingUiState())
    val state: StateFlow<AddSpendingUiState> = _state

    init {
        loadBudget()
    }

    private fun loadBudget() {
        viewModelScope.launch {
            val cached = cache.load()
            if (cached != null) {
                applyBudget(cached)
            } else {
                when (val result = sync.fetchBudget()) {
                    is SyncState.Ready -> applyBudget(result.budget)
                    else -> _state.value = _state.value.copy(
                        isLoadingBudget = false,
                        error = "Could not load budget — sync first",
                    )
                }
            }
        }
    }

    private fun applyBudget(budget: BudgetJson) {
        val myName = creds.load()?.myName ?: budget.metadata.couple.firstOrNull() ?: "User 1"
        _state.value = _state.value.copy(
            budget = budget,
            isLoadingBudget = false,
            selectedCategoryId = budget.categories.firstOrNull()?.id ?: "",
            selectedUser = myName,
            coupleNames = budget.metadata.couple,
        )
    }

    fun onCategory(id: String) { _state.value = _state.value.copy(selectedCategoryId = id) }
    fun onAmount(v: String) { _state.value = _state.value.copy(amount = v, error = null) }
    fun onDescription(v: String) { _state.value = _state.value.copy(description = v) }
    fun onDate(v: LocalDate) { _state.value = _state.value.copy(date = v) }
    fun onUser(v: String) { _state.value = _state.value.copy(selectedUser = v) }

    fun loadExistingEntry(entryId: String) {
        val budget = _state.value.budget ?: return
        val entry = budget.spending.firstOrNull { it.id == entryId } ?: return
        _state.value = _state.value.copy(
            editingEntryId = entryId,
            selectedCategoryId = entry.categoryId,
            amount = entry.amount.toString(),
            description = entry.description,
            date = LocalDate.parse(entry.date),
            selectedUser = entry.user,
        )
    }

    fun submit() {
        val s = _state.value
        val amountVal = s.amount.toDoubleOrNull()
        if (amountVal == null || amountVal <= 0) {
            _state.value = s.copy(error = "Enter a valid amount")
            return
        }
        if (s.selectedCategoryId.isEmpty()) {
            _state.value = s.copy(error = "Select a category")
            return
        }

        _state.value = s.copy(isSaving = true, error = null)

        if (s.editingEntryId != null) {
            val updated = SpendingEntryJson(
                id = s.editingEntryId,
                date = s.date.toString(),
                categoryId = s.selectedCategoryId,
                amount = amountVal,
                description = s.description.trim(),
                user = s.selectedUser,
            )
            viewModelScope.launch {
                when (val result = sync.updateEntry(updated, s.selectedUser)) {
                    is SyncState.Ready,
                    is SyncState.SavedOffline -> _state.value = _state.value.copy(isSaving = false, saved = true)
                    is SyncState.SyncError -> _state.value = _state.value.copy(isSaving = false, error = result.message)
                    is SyncState.AuthError -> _state.value = _state.value.copy(isSaving = false, error = "Authentication failed")
                    else -> _state.value = _state.value.copy(isSaving = false)
                }
            }
        } else {
            val entry = SpendingEntryJson(
                id = UUID.randomUUID().toString(),
                date = s.date.toString(),
                categoryId = s.selectedCategoryId,
                amount = amountVal,
                description = s.description.trim(),
                user = s.selectedUser,
            )
            viewModelScope.launch {
                when (val result = sync.addEntry(entry, s.selectedUser)) {
                    is SyncState.Ready,
                    is SyncState.SavedOffline -> _state.value = _state.value.copy(isSaving = false, saved = true)
                    is SyncState.SyncError -> _state.value = _state.value.copy(isSaving = false, error = result.message)
                    is SyncState.AuthError -> _state.value = _state.value.copy(isSaving = false, error = "Authentication failed")
                    is SyncState.ConflictDetected -> _state.value = _state.value.copy(
                        isSaving = false,
                        error = "Conflict after auto-merge failed. Pull to refresh and try again.",
                    )
                    else -> _state.value = _state.value.copy(isSaving = false)
                }
            }
        }
    }
}
