package com.kopilka.android.ui.setup

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kopilka.android.data.storage.CredentialStore
import com.kopilka.android.data.storage.WebDavCredentials
import com.kopilka.android.data.sync.SyncManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class SetupUiState(
    val url: String = "https://webdav.pcloud.com",
    val username: String = "",
    val password: String = "",
    val remotePath: String = "Kopilka/budget.json",
    val myName: String = "",
    val partnerName: String = "",
    val isTesting: Boolean = false,
    val testResult: String? = null,
    val testSuccess: Boolean = false,
    val canSave: Boolean = false,
)

class SetupViewModel(app: Application) : AndroidViewModel(app) {

    private val creds = CredentialStore(app)
    private val sync = SyncManager(app)

    private val _state = MutableStateFlow(SetupUiState())
    val state: StateFlow<SetupUiState> = _state

    init {
        creds.load()?.let { c ->
            _state.value = _state.value.copy(
                url = c.webdavUrl,
                username = c.username,
                password = c.password,
                remotePath = c.remotePath,
                myName = c.myName,
            )
        }
        recheck()
    }

    fun onUrl(v: String) { _state.value = _state.value.copy(url = v, testResult = null); recheck() }
    fun onUsername(v: String) { _state.value = _state.value.copy(username = v, testResult = null); recheck() }
    fun onPassword(v: String) { _state.value = _state.value.copy(password = v, testResult = null); recheck() }
    fun onRemotePath(v: String) { _state.value = _state.value.copy(remotePath = v, testResult = null); recheck() }
    fun onMyName(v: String) { _state.value = _state.value.copy(myName = v); recheck() }

    fun testConnection() {
        _state.value = _state.value.copy(isTesting = true, testResult = null)
        viewModelScope.launch {
            val s = _state.value
            creds.save(WebDavCredentials(s.url, s.username, s.password, s.remotePath, s.myName))
            val (ok, msg) = sync.testConnection()
            _state.value = _state.value.copy(isTesting = false, testResult = msg, testSuccess = ok)
        }
    }

    fun save(): Boolean {
        val s = _state.value
        if (!s.canSave) return false
        creds.save(WebDavCredentials(s.url, s.username, s.password, s.remotePath, s.myName))
        return true
    }

    private fun recheck() {
        val s = _state.value
        _state.value = s.copy(
            canSave = s.url.isNotBlank() && s.username.isNotBlank()
                    && s.password.isNotBlank() && s.myName.isNotBlank()
        )
    }
}
