package com.kopilka.android.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.kopilka.android.data.model.BudgetJson
import com.kopilka.android.data.model.SpendingEntryJson
import com.kopilka.android.data.storage.BudgetCache
import com.kopilka.android.data.storage.CredentialStore
import com.kopilka.android.domain.buildCategoryStates
import com.kopilka.android.util.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

sealed class SyncState {
    data object Idle : SyncState()
    data object Loading : SyncState()
    data class Ready(val budget: BudgetJson, val syncedAt: Long) : SyncState()
    data class ConflictDetected(
        val local: BudgetJson,
        val remote: BudgetJson,
        val remoteEtag: String,
        val remoteModifiedBy: String,
    ) : SyncState()
    data class SyncError(val message: String) : SyncState()
    data object SavedOffline : SyncState()
    data object AuthError : SyncState()
    data object NotConfigured : SyncState()
}

internal val syncJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

internal val isoNow: String
    get() = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
        .withZone(ZoneOffset.UTC)
        .format(Instant.now())

/** Merge remote (authoritative base) with any entries in local that aren't in remote. */
internal fun mergeBudgets(local: BudgetJson, remote: BudgetJson, userName: String): BudgetJson {
    val remoteIds = remote.spending.map { it.id }.toSet()
    val onlyInLocal = local.spending.filter { it.id !in remoteIds }
    return remote.copy(
        spending = remote.spending + onlyInLocal,
        metadata = remote.metadata.copy(
            lastModified = isoNow,
            lastModifiedBy = userName,
        ),
    )
}

internal fun enqueueUploadWork(context: Context) {
    val request = OneTimeWorkRequestBuilder<UploadWorker>()
        .setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
        )
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
        .build()
    WorkManager.getInstance(context)
        .enqueueUniqueWork("kopilka_sync", ExistingWorkPolicy.REPLACE, request)
}

class SyncManager(private val context: Context) {

    private val cache = BudgetCache(context)
    private val creds = CredentialStore(context)
    private val notif = NotificationHelper(context)

    fun hasPendingUpload(): Boolean = cache.hasPendingUpload()

    suspend fun fetchBudget(): SyncState = withContext(Dispatchers.IO) {
        val c = creds.load() ?: return@withContext SyncState.NotConfigured
        val client = WebDavClient(c.webdavUrl, c.username, c.password)

        when (val r = client.get(c.remotePath)) {
            is WebDavResult.AuthFailure -> SyncState.AuthError
            is WebDavResult.Error -> {
                val cached = cache.load()
                if (cached != null) SyncState.Ready(cached, cache.loadedAt())
                else SyncState.SyncError(r.message)
            }
            is WebDavResult.Success -> {
                val (bytes, etag) = r.value
                val budget = try {
                    syncJson.decodeFromString<BudgetJson>(bytes.decodeToString())
                } catch (e: Exception) {
                    return@withContext SyncState.SyncError("Parse error: ${e.message}")
                }
                // Notify if partner logged new entries since our last sync
                val cached = cache.load()
                if (cached != null) {
                    val cachedIds = cached.spending.map { it.id }.toSet()
                    val newPartnerEntries = budget.spending.filter { it.id !in cachedIds && it.user != c.myName }
                    if (newPartnerEntries.isNotEmpty()) {
                        val total = newPartnerEntries.sumOf { it.amount }
                        val partnerName = newPartnerEntries.first().user
                        val desc = if (newPartnerEntries.size == 1) newPartnerEntries.first().description
                                   else "${newPartnerEntries.size} new entries"
                        notif.showPartnerSpent(partnerName, newPartnerEntries.size, total, desc)
                    }
                }
                // Fire budget alerts for categories that newly crossed 80%
                if (cached != null) {
                    val prevStates = buildCategoryStates(cached).associateBy { it.id }
                    val newStates = buildCategoryStates(budget)
                    newStates.forEach { newCat ->
                        val prevProgress = prevStates[newCat.id]?.progress ?: 0f
                        val newPct = (newCat.progress * 100).toInt()
                        if (prevProgress < 0.80f && newCat.progress >= 0.80f) {
                            notif.showBudgetAlert(newCat.name, newPct)
                        }
                    }
                }
                cache.save(budget, etag)
                SyncState.Ready(budget, System.currentTimeMillis())
            }
            else -> SyncState.SyncError("Unexpected result")
        }
    }

    suspend fun addEntry(entry: SpendingEntryJson, userName: String): SyncState =
        withContext(Dispatchers.IO) {
            val c = creds.load() ?: return@withContext SyncState.NotConfigured
            val client = WebDavClient(c.webdavUrl, c.username, c.password)

            val current = cache.load()
                ?: return@withContext SyncState.SyncError("No local budget — sync first")
            val knownEtag = cache.loadedEtag()

            val updated = current.copy(
                spending = current.spending + entry,
                metadata = current.metadata.copy(
                    lastModified = isoNow,
                    lastModifiedBy = userName,
                ),
            )

            cache.save(updated, knownEtag)

            notif.showSyncing()
            try {
                putWithMergeRetry(client, c.remotePath, updated, knownEtag, userName)
            } finally {
                notif.hideSyncing()
            }
        }

    suspend fun deleteEntry(entryId: String, userName: String): SyncState =
        withContext(Dispatchers.IO) {
            val c = creds.load() ?: return@withContext SyncState.NotConfigured
            val client = WebDavClient(c.webdavUrl, c.username, c.password)
            val current = cache.load() ?: return@withContext SyncState.SyncError("No local budget")
            val knownEtag = cache.loadedEtag()
            val updated = current.copy(
                spending = current.spending.filter { it.id != entryId },
                metadata = current.metadata.copy(lastModified = isoNow, lastModifiedBy = userName),
            )
            cache.save(updated, knownEtag)
            notif.showSyncing()
            try {
                putWithMergeRetry(client, c.remotePath, updated, knownEtag, userName)
            } finally {
                notif.hideSyncing()
            }
        }

    suspend fun updateEntry(entry: SpendingEntryJson, userName: String): SyncState =
        withContext(Dispatchers.IO) {
            val c = creds.load() ?: return@withContext SyncState.NotConfigured
            val client = WebDavClient(c.webdavUrl, c.username, c.password)
            val current = cache.load() ?: return@withContext SyncState.SyncError("No local budget")
            val knownEtag = cache.loadedEtag()
            val updated = current.copy(
                spending = current.spending.map { if (it.id == entry.id) entry else it },
                metadata = current.metadata.copy(lastModified = isoNow, lastModifiedBy = userName),
            )
            cache.save(updated, knownEtag)
            notif.showSyncing()
            try {
                putWithMergeRetry(client, c.remotePath, updated, knownEtag, userName)
            } finally {
                notif.hideSyncing()
            }
        }

    private suspend fun putWithMergeRetry(
        client: WebDavClient,
        path: String,
        local: BudgetJson,
        etag: String,
        userName: String,
    ): SyncState {
        val bytes = syncJson.encodeToString(BudgetJson.serializer(), local).encodeToByteArray()

        return when (val r = client.put(path, bytes, etag.ifEmpty { null })) {
            is WebDavResult.Success -> {
                cache.save(local, r.value)
                cache.setPendingUpload(false)
                SyncState.Ready(local, System.currentTimeMillis())
            }

            is WebDavResult.Conflict -> {
                when (val remote = client.get(path)) {
                    is WebDavResult.Success -> {
                        val (remoteBytes, remoteEtag) = remote.value
                        val remoteBudget = try {
                            syncJson.decodeFromString<BudgetJson>(remoteBytes.decodeToString())
                        } catch (e: Exception) {
                            return SyncState.SyncError("Conflict + parse error: ${e.message}")
                        }
                        val merged = mergeBudgets(local, remoteBudget, userName)
                        val mergedBytes =
                            syncJson.encodeToString(BudgetJson.serializer(), merged).encodeToByteArray()

                        when (val retry = client.put(path, mergedBytes, remoteEtag)) {
                            is WebDavResult.Success -> {
                                cache.save(merged, retry.value)
                                cache.setPendingUpload(false)
                                SyncState.Ready(merged, System.currentTimeMillis())
                            }
                            else -> SyncState.ConflictDetected(
                                local = merged,
                                remote = remoteBudget,
                                remoteEtag = remoteEtag,
                                remoteModifiedBy = remoteBudget.metadata.lastModifiedBy,
                            )
                        }
                    }
                    else -> SyncState.SyncError("Conflict: could not fetch remote to merge")
                }
            }

            is WebDavResult.AuthFailure -> SyncState.AuthError

            is WebDavResult.Error -> {
                if (r.message.startsWith("Network error")) {
                    cache.setPendingUpload(true)
                    enqueueUploadWork(context)
                    SyncState.SavedOffline
                } else {
                    SyncState.SyncError(r.message)
                }
            }
        }
    }

    suspend fun forcePush(local: BudgetJson): SyncState = withContext(Dispatchers.IO) {
        val c = creds.load() ?: return@withContext SyncState.NotConfigured
        val client = WebDavClient(c.webdavUrl, c.username, c.password)
        val bytes = syncJson.encodeToString(BudgetJson.serializer(), local).encodeToByteArray()
        notif.showSyncing()
        try {
            when (val r = client.put(c.remotePath, bytes, null)) {
                is WebDavResult.Success -> {
                    cache.save(local, r.value)
                    SyncState.Ready(local, System.currentTimeMillis())
                }
                is WebDavResult.AuthFailure -> SyncState.AuthError
                is WebDavResult.Error -> SyncState.SyncError(r.message)
                else -> SyncState.SyncError("Unexpected result during force push")
            }
        } finally {
            notif.hideSyncing()
        }
    }

    suspend fun acceptRemote(remote: BudgetJson, remoteEtag: String): SyncState {
        cache.save(remote, remoteEtag)
        return SyncState.Ready(remote, System.currentTimeMillis())
    }

    fun hasCachedBudget(): Boolean = cache.load() != null

    suspend fun testConnection(): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val c = creds.load() ?: return@withContext false to "WebDAV is not configured"
        val client = WebDavClient(c.webdavUrl, c.username, c.password)
        when (val r = client.testConnection(c.remotePath)) {
            is WebDavResult.Success -> true to "Connection successful"
            is WebDavResult.AuthFailure -> false to "Authentication failed — check username and password"
            is WebDavResult.Error -> false to r.message
            else -> false to "Unexpected error"
        }
    }
}
