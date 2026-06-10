package com.kopilka.android.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kopilka.android.data.model.BudgetJson
import com.kopilka.android.data.storage.BudgetCache
import com.kopilka.android.data.storage.CredentialStore
import com.kopilka.android.util.NotificationHelper

class UploadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val creds = CredentialStore(applicationContext).load() ?: return Result.failure()
        val cache = BudgetCache(applicationContext)
        val budget = cache.load() ?: return Result.failure()
        val etag = cache.loadedEtag()

        val client = WebDavClient(creds.webdavUrl, creds.username, creds.password)
        val notif = NotificationHelper(applicationContext)
        notif.showSyncing()

        try {
            val toUpload = budget.copy(
                metadata = budget.metadata.copy(
                    lastModified = isoNow,
                    lastModifiedBy = creds.myName,
                )
            )
            val bytes = syncJson.encodeToString(BudgetJson.serializer(), toUpload).encodeToByteArray()

            return when (val r = client.put(creds.remotePath, bytes, etag.ifEmpty { null })) {
                is WebDavResult.Success -> {
                    cache.save(toUpload, r.value)
                    cache.setPendingUpload(false)
                    Result.success()
                }
                is WebDavResult.Conflict -> {
                    when (val remote = client.get(creds.remotePath)) {
                        is WebDavResult.Success -> {
                            val (remoteBytes, remoteEtag) = remote.value
                            val remoteBudget = syncJson.decodeFromString<BudgetJson>(remoteBytes.decodeToString())
                            val merged = mergeBudgets(toUpload, remoteBudget, creds.myName)
                            val mergedBytes = syncJson.encodeToString(BudgetJson.serializer(), merged).encodeToByteArray()
                            when (val retry = client.put(creds.remotePath, mergedBytes, remoteEtag)) {
                                is WebDavResult.Success -> {
                                    cache.save(merged, retry.value)
                                    cache.setPendingUpload(false)
                                    Result.success()
                                }
                                else -> if (runAttemptCount < 2) Result.retry() else Result.failure()
                            }
                        }
                        else -> Result.retry()
                    }
                }
                is WebDavResult.AuthFailure -> Result.failure()
                is WebDavResult.Error -> if (runAttemptCount < 3) Result.retry() else Result.failure()
                else -> Result.retry()
            }
        } finally {
            notif.hideSyncing()
        }
    }
}
