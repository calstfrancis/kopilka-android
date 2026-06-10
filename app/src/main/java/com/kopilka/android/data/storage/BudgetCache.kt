package com.kopilka.android.data.storage

import android.content.Context
import com.kopilka.android.data.model.BudgetJson
import kotlinx.serialization.json.Json
import java.io.File

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

class BudgetCache(private val context: Context) {

    private val cacheFile: File get() = File(context.filesDir, "budget_cache.json")
    private val metaPrefs get() = context.getSharedPreferences("budget_cache_meta", Context.MODE_PRIVATE)

    fun save(budget: BudgetJson, etag: String) {
        cacheFile.writeText(json.encodeToString(BudgetJson.serializer(), budget))
        metaPrefs.edit()
            .putString("etag", etag)
            .putLong("loaded_at", System.currentTimeMillis())
            .apply()
    }

    fun load(): BudgetJson? {
        if (!cacheFile.exists()) return null
        return try {
            json.decodeFromString<BudgetJson>(cacheFile.readText())
        } catch (e: Exception) {
            null
        }
    }

    fun loadedEtag(): String = metaPrefs.getString("etag", "") ?: ""

    fun loadedAt(): Long = metaPrefs.getLong("loaded_at", 0L)

    fun setPendingUpload(pending: Boolean) {
        metaPrefs.edit().putBoolean("pending_upload", pending).apply()
    }

    fun hasPendingUpload(): Boolean = metaPrefs.getBoolean("pending_upload", false)

    fun clear() {
        cacheFile.delete()
        metaPrefs.edit().clear().apply()
    }
}
