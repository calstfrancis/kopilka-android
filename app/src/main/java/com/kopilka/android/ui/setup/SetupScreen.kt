package com.kopilka.android.ui.setup

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    onDone: () -> Unit,
    vm: SetupViewModel = viewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var showPassword by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("WebDAV Setup") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(8.dp))

            Text(
                "Connect to the same WebDAV account used by the desktop app.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(4.dp))

            // Provider quick-select
            Text("Provider shortcuts", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SuggestionChip(onClick = { vm.onUrl("https://webdav.pcloud.com") }, label = { Text("pCloud") })
                SuggestionChip(onClick = { vm.onUrl("https://ewebdav.pcloud.com") }, label = { Text("pCloud EU") })
            }

            OutlinedTextField(
                value = state.url,
                onValueChange = vm::onUrl,
                label = { Text("WebDAV URL") },
                placeholder = { Text("https://webdav.pcloud.com") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.username,
                onValueChange = vm::onUsername,
                label = { Text("Username / Email") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.password,
                onValueChange = vm::onPassword,
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (showPassword) "Hide password" else "Show password",
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.remotePath,
                onValueChange = vm::onRemotePath,
                label = { Text("Remote file path") },
                placeholder = { Text("Kopilka/budget.json") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.myName,
                onValueChange = vm::onMyName,
                label = { Text("Your name (as it appears in budget)") },
                placeholder = { Text("e.g. Cal") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // Test result banner
            state.testResult?.let { msg ->
                val containerColor = if (state.testSuccess)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.errorContainer
                val contentColor = if (state.testSuccess)
                    MaterialTheme.colorScheme.onPrimaryContainer
                else
                    MaterialTheme.colorScheme.onErrorContainer

                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = containerColor,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        msg,
                        color = contentColor,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedButton(
                    onClick = { vm.testConnection() },
                    enabled = state.canSave && !state.isTesting,
                    modifier = Modifier.weight(1f),
                ) {
                    if (state.isTesting) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("Test Connection")
                }

                Button(
                    onClick = { if (vm.save()) onDone() },
                    enabled = state.canSave,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Save & Continue")
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
