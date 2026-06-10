package com.kopilka.android.data.storage

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

data class WebDavCredentials(
    val webdavUrl: String,
    val username: String,
    val password: String,
    val remotePath: String,
    val myName: String,
)

class CredentialStore(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "kopilka_credentials",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun save(creds: WebDavCredentials) {
        prefs.edit()
            .putString("webdav_url", creds.webdavUrl)
            .putString("username", creds.username)
            .putString("password", creds.password)
            .putString("remote_path", creds.remotePath)
            .putString("my_name", creds.myName)
            .apply()
    }

    fun load(): WebDavCredentials? {
        val url = prefs.getString("webdav_url", null) ?: return null
        val username = prefs.getString("username", null) ?: return null
        val password = prefs.getString("password", null) ?: return null
        val path = prefs.getString("remote_path", "Kopilka/budget.json") ?: "Kopilka/budget.json"
        val name = prefs.getString("my_name", "User 1") ?: "User 1"
        return WebDavCredentials(url, username, password, path, name)
    }

    fun isConfigured(): Boolean = prefs.contains("webdav_url")

    fun clear() = prefs.edit().clear().apply()
}
