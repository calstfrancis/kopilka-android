package com.kopilka.android.ui.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kopilka.android.domain.CategoryUiState
import com.kopilka.android.domain.RecurringUiState
import com.kopilka.android.domain.RecentEntryUiState
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(
    onOpenSetup: () -> Unit,
    onAddSpending: () -> Unit,
    onEditEntry: (String) -> Unit,
    onViewAll: () -> Unit,
    vm: CategoriesViewModel = viewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()

    state.conflict?.let { conflict ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Sync conflict") },
            text = { Text("${conflict.remoteModifiedBy} saved the budget while you were adding. What do you want to do?") },
            confirmButton = { TextButton(onClick = { vm.onConflictForcePush() }) { Text("Keep my entry") } },
            dismissButton = { TextButton(onClick = { vm.onConflictKeepRemote() }) { Text("Use their version") } },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kopilka") },
                actions = {
                    state.lastSynced?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 4.dp),
                        )
                    }
                    BadgedBox(
                        badge = {
                            if (state.pendingUpload) {
                                Badge()
                            }
                        },
                    ) {
                        IconButton(onClick = { vm.refresh() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                    }
                    IconButton(onClick = onOpenSetup) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        floatingActionButton = {
            if (state.budget != null && state.categories.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = onAddSpending,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Add Spending") },
                )
            }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isLoading,
            onRefresh = { vm.refresh() },
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            when {
                state.isLoading && state.categories.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                state.syncError != null && state.categories.isEmpty() -> {
                    Column(
                        Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(state.syncError!!, color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { vm.refresh() }) { Text("Retry") }
                    }
                }
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (state.syncError != null) {
                            item {
                                Surface(
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    shape = MaterialTheme.shapes.medium,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        "Offline — showing cached data",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.padding(12.dp),
                                    )
                                }
                            }
                        }

                        items(state.categories, key = { it.id }) { cat ->
                            CategoryCard(cat)
                        }

                        // Recurring quick-log section
                        if (state.recurringEntries.isNotEmpty()) {
                            item {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Recurring",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 4.dp),
                                )
                            }
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = MaterialTheme.shapes.large,
                                ) {
                                    Column {
                                        state.recurringEntries.forEachIndexed { i, rec ->
                                            RecurringEntryRow(
                                                rec = rec,
                                                onLog = { vm.quickLogRecurring(rec) },
                                            )
                                            if (i < state.recurringEntries.lastIndex) {
                                                HorizontalDivider(
                                                    modifier = Modifier.padding(horizontal = 16.dp),
                                                    color = MaterialTheme.colorScheme.outlineVariant,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Recent entries
                        if (state.recentEntries.isNotEmpty()) {
                            item {
                                Spacer(Modifier.height(4.dp))
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        "Recent",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    TextButton(onClick = onViewAll) { Text("See all") }
                                }
                            }
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = MaterialTheme.shapes.large,
                                ) {
                                    Column {
                                        state.recentEntries.forEachIndexed { i, entry ->
                                            RecentEntryRow(
                                                entry = entry,
                                                onEdit = { onEditEntry(entry.id) },
                                                onDelete = { vm.deleteEntry(entry.id) },
                                            )
                                            if (i < state.recentEntries.lastIndex) {
                                                HorizontalDivider(
                                                    modifier = Modifier.padding(horizontal = 16.dp),
                                                    color = MaterialTheme.colorScheme.outlineVariant,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        item { Spacer(Modifier.height(80.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryCard(cat: CategoryUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                cat.color?.let { c ->
                    Box(
                        Modifier
                            .size(12.dp)
                            .background(c, shape = MaterialTheme.shapes.small)
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    cat.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "$${String.format("%.0f", cat.spent)}/$${String.format("%.0f", cat.budgetAmount)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (cat.isOverBudget) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            val progressColor = when {
                cat.isOverBudget -> MaterialTheme.colorScheme.error
                cat.progress > 0.85f -> MaterialTheme.colorScheme.tertiary
                else -> cat.color ?: MaterialTheme.colorScheme.primary
            }

            LinearProgressIndicator(
                progress = { cat.progress },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = progressColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    cat.periodLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (cat.isOverBudget) {
                    Text(
                        "over by $${String.format("%.2f", cat.spent - cat.budgetAmount)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            if (cat.dailySpend.isNotEmpty() && cat.dailySpend.any { it > 0f }) {
                SpendingSparkline(
                    dailySpend = cat.dailySpend,
                    barColor = cat.color ?: MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun RecurringEntryRow(rec: RecurringUiState, onLog: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        rec.categoryColor?.let { c ->
            Box(Modifier.size(8.dp).background(c, MaterialTheme.shapes.small))
        }
        Column(Modifier.weight(1f)) {
            Text(rec.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(
                "${rec.categoryName} · ${rec.frequency}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            "$${String.format("%.2f", rec.amount)}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
        FilledTonalButton(
            onClick = onLog,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            modifier = Modifier.height(32.dp),
        ) {
            Text("Log", style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun RecentEntryRow(
    entry: RecentEntryUiState,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        entry.categoryColor?.let { c ->
            Box(Modifier.size(8.dp).background(c, MaterialTheme.shapes.small))
        }

        Column(Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    entry.categoryName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (entry.description.isNotBlank()) {
                    Text("·", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        entry.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(
                "${entry.user} · ${formatDate(entry.date)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }

        Text(
            "$${String.format("%.2f", entry.amount)}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )

        Box {
            IconButton(onClick = { showMenu = true }, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "Options",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(
                    text = { Text("Edit") },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                    onClick = { showMenu = false; onEdit() },
                )
                DropdownMenuItem(
                    text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                    leadingIcon = {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    },
                    onClick = { showMenu = false; onDelete() },
                )
            }
        }
    }
}

private val dateFmt = DateTimeFormatter.ofPattern("MMM d")
private fun formatDate(iso: String): String = try {
    LocalDate.parse(iso).format(dateFmt)
} catch (_: Exception) {
    iso
}
