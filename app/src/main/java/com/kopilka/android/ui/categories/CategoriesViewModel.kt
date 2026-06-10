package com.kopilka.android.ui.categories

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kopilka.android.data.model.BudgetJson
import com.kopilka.android.data.model.SpendingEntryJson
import com.kopilka.android.data.storage.BudgetCache
import com.kopilka.android.data.storage.CredentialStore
import com.kopilka.android.data.sync.SyncManager
import com.kopilka.android.data.sync.SyncState
import com.kopilka.android.domain.CategoryUiState
import com.kopilka.android.domain.RecurringUiState
import com.kopilka.android.domain.RecentEntryUiState
import com.kopilka.android.domain.buildCategoryStates
import com.kopilka.android.domain.buildRecentEntries
import com.kopilka.android.domain.buildRecurringEntries
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date
import java.util.Locale
import java.util.UUID

data class CategoriesScreenState(
    val categories: List<CategoryUiState> = emptyList(),
    val recentEntries: List<RecentEntryUiState> = emptyList(),
    val recurringEntries: List<RecurringUiState> = emptyList(),
    val isLoading: Boolean = false,
    val syncError: String? = null,
    val lastSynced: String? = null,
    val coupleNames: List<String> = listOf("User 1", "User 2"),
    val myName: String = "",
    val conflict: ConflictState? = null,
    val budget: BudgetJson? = null,
    val pendingUpload: Boolean = false,
)

data class ConflictState(
    val local: BudgetJson,
    val remote: BudgetJson,
    val remoteEtag: String,
    val remoteModifiedBy: String,
)

class CategoriesViewModel(app: Application) : AndroidViewModel(app) {

    private val sync = SyncManager(app)
    private val creds = CredentialStore(app)
    private val cache = BudgetCache(app)

    private val _state = MutableStateFlow(CategoriesScreenState())
    val state: StateFlow<CategoriesScreenState> = _state

    init {
        _state.value = _state.value.copy(
            myName = creds.load()?.myName ?: "",
            pendingUpload = sync.hasPendingUpload(),
        )
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, syncError = null)
            when (val result = sync.fetchBudget()) {
                is SyncState.Ready -> applyBudget(result.budget, result.syncedAt)
                is SyncState.SyncError -> _state.value = _state.value.copy(
                    isLoading = false, syncError = result.message,
                )
                is SyncState.AuthError -> _state.value = _state.value.copy(
                    isLoading = false, syncError = "Authentication failed — check setup",
                )
                is SyncState.NotConfigured -> _state.value = _state.value.copy(
                    isLoading = false, syncError = "WebDAV not configured",
                )
                else -> _state.value = _state.value.copy(isLoading = false)
            }
        }
    }

    private fun applyBudget(budget: BudgetJson, syncedAt: Long) {
        val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        _state.value = _state.value.copy(
            isLoading = false,
            syncError = null,
            categories = buildCategoryStates(budget),
            recentEntries = buildRecentEntries(budget),
            recurringEntries = buildRecurringEntries(budget),
            coupleNames = budget.metadata.couple,
            lastSynced = "synced ${fmt.format(Date(syncedAt))}",
            conflict = null,
            budget = budget,
            pendingUpload = sync.hasPendingUpload(),
        )
    }

    fun deleteEntry(entryId: String) {
        val myName = _state.value.myName
        viewModelScope.launch {
            // Optimistic UI update
            val current = _state.value.budget ?: return@launch
            val optimistic = current.copy(spending = current.spending.filter { it.id != entryId })
            applyBudget(optimistic, System.currentTimeMillis())

            when (val r = sync.deleteEntry(entryId, myName)) {
                is SyncState.Ready -> applyBudget(r.budget, r.syncedAt)
                is SyncState.SavedOffline -> _state.value = _state.value.copy(pendingUpload = true)
                else -> {}
            }
        }
    }

    fun quickLogRecurring(rec: RecurringUiState) {
        val myName = _state.value.myName.ifEmpty { rec.user }
        val entry = SpendingEntryJson(
            id = UUID.randomUUID().toString(),
            date = LocalDate.now().toString(),
            categoryId = rec.categoryId,
            amount = rec.amount,
            description = rec.name,
            user = myName,
        )
        viewModelScope.launch {
            when (val r = sync.addEntry(entry, myName)) {
                is SyncState.Ready -> applyBudget(r.budget, r.syncedAt)
                is SyncState.SavedOffline -> {
                    _state.value = _state.value.copy(pendingUpload = true)
                    refresh()
                }
                else -> {}
            }
        }
    }

    fun onConflictKeepRemote() {
        val conflict = _state.value.conflict ?: return
        viewModelScope.launch {
            when (val r = sync.acceptRemote(conflict.remote, conflict.remoteEtag)) {
                is SyncState.Ready -> applyBudget(r.budget, r.syncedAt)
                else -> {}
            }
        }
    }

    fun onConflictForcePush() {
        val conflict = _state.value.conflict ?: return
        viewModelScope.launch {
            when (val r = sync.forcePush(conflict.local)) {
                is SyncState.Ready -> applyBudget(r.budget, r.syncedAt)
                is SyncState.SyncError -> _state.value = _state.value.copy(
                    isLoading = false, syncError = r.message, conflict = null,
                )
                else -> {}
            }
        }
    }
}
