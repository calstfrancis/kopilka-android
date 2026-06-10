package com.kopilka.android.ui.addspending

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kopilka.android.data.model.ONE_TIME_CATEGORY_ID
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSpendingScreen(
    onDone: () -> Unit,
    existingEntryId: String? = null,
    vm: AddSpendingViewModel = viewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.saved) {
        if (state.saved) onDone()
    }

    // Load existing entry once the budget is ready
    LaunchedEffect(existingEntryId, state.budget) {
        if (existingEntryId != null && state.budget != null && state.editingEntryId == null) {
            vm.loadExistingEntry(existingEntryId)
        }
    }

    val isEditMode = state.editingEntryId != null

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEditMode) "Edit Entry" else "Add Spending") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.isLoadingBudget -> {
                Box(
                    Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
            }

            state.budget == null -> {
                Box(
                    Modifier.fillMaxSize().padding(padding).padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        state.error ?: "Budget unavailable",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            else -> {
                val budget = state.budget!!
                Column(
                    modifier = Modifier
                        .padding(padding)
                        .padding(horizontal = 16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Spacer(Modifier.height(8.dp))

                    // Category
                    var catExpanded by remember { mutableStateOf(false) }
                    val selectedCatName = when (state.selectedCategoryId) {
                        ONE_TIME_CATEGORY_ID -> "One-time Purchase"
                        "" -> "Select category"
                        else -> budget.categories.firstOrNull { it.id == state.selectedCategoryId }?.name ?: "Select category"
                    }

                    ExposedDropdownMenuBox(expanded = catExpanded, onExpandedChange = { catExpanded = it }) {
                        OutlinedTextField(
                            value = selectedCatName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Category") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(catExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                        )
                        ExposedDropdownMenu(expanded = catExpanded, onDismissRequest = { catExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text("One-time Purchase") },
                                onClick = { vm.onCategory(ONE_TIME_CATEGORY_ID); catExpanded = false },
                            )
                            HorizontalDivider()
                            budget.categories.forEach { cat ->
                                DropdownMenuItem(
                                    text = { Text(cat.name) },
                                    onClick = { vm.onCategory(cat.id); catExpanded = false },
                                )
                            }
                        }
                    }

                    // Amount
                    OutlinedTextField(
                        value = state.amount,
                        onValueChange = vm::onAmount,
                        label = { Text("Amount (${budget.config.currency})") },
                        placeholder = { Text("0.00") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        prefix = { Text("$") },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    // Description
                    OutlinedTextField(
                        value = state.description,
                        onValueChange = vm::onDescription,
                        label = { Text("Description") },
                        placeholder = { Text("e.g. Metro grocery run") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    // Date
                    DateField(state.date, vm::onDate)

                    // Who
                    if (state.coupleNames.size >= 2) {
                        var userExpanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(expanded = userExpanded, onExpandedChange = { userExpanded = it }) {
                            OutlinedTextField(
                                value = state.selectedUser,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Who") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(userExpanded) },
                                modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                            )
                            ExposedDropdownMenu(expanded = userExpanded, onDismissRequest = { userExpanded = false }) {
                                state.coupleNames.forEach { name ->
                                    DropdownMenuItem(
                                        text = { Text(name) },
                                        onClick = { vm.onUser(name); userExpanded = false },
                                    )
                                }
                            }
                        }
                    }

                    state.error?.let { err ->
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                err,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(12.dp),
                            )
                        }
                    }

                    Button(
                        onClick = { vm.submit() },
                        enabled = !state.isSaving,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (state.isSaving) {
                            CircularProgressIndicator(
                                Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(if (isEditMode) "Save Changes" else "Add to Log")
                    }

                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(date: LocalDate, onDate: (LocalDate) -> Unit) {
    var showPicker by remember { mutableStateOf(false) }
    val fmt = DateTimeFormatter.ofPattern("MMM d, yyyy")

    OutlinedTextField(
        value = date.format(fmt),
        onValueChange = {},
        readOnly = true,
        label = { Text("Date") },
        modifier = Modifier.fillMaxWidth(),
        trailingIcon = { TextButton(onClick = { showPicker = true }) { Text("Change") } },
    )

    if (showPicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = date.toEpochDay() * 86_400_000L,
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { ms ->
                        onDate(LocalDate.ofEpochDay(ms / 86_400_000L))
                    }
                    showPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = pickerState)
        }
    }
}
