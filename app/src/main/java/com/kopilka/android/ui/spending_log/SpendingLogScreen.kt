package com.kopilka.android.ui.spending_log

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kopilka.android.domain.RecentEntryUiState
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpendingLogScreen(
    onBack: () -> Unit,
    onEditEntry: (String) -> Unit,
    myName: String,
    vm: SpendingLogViewModel = viewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Spending Log") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Period filter chips
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LogPeriod.entries.forEach { period ->
                    FilterChip(
                        selected = state.period == period,
                        onClick = { vm.setPeriod(period) },
                        label = { Text(period.label) },
                    )
                }
            }

            when {
                state.isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                state.error != null -> {
                    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(state.error!!, color = MaterialTheme.colorScheme.error)
                    }
                }
                state.entries.isEmpty() -> {
                    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("No entries for this period", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                else -> {
                    val grouped = state.entries.groupBy { it.date }
                    LazyColumn(contentPadding = PaddingValues(bottom = 80.dp)) {
                        grouped.forEach { (date, dayEntries) ->
                            item(key = "header_$date") {
                                Text(
                                    formatDayHeader(date),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                )
                            }
                            item(key = "card_$date") {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp),
                                    shape = MaterialTheme.shapes.large,
                                ) {
                                    Column {
                                        dayEntries.forEachIndexed { i, entry ->
                                            LogEntryRow(
                                                entry = entry,
                                                onEdit = { onEditEntry(entry.id) },
                                                onDelete = { vm.deleteEntry(entry.id, myName) },
                                            )
                                            if (i < dayEntries.lastIndex) {
                                                HorizontalDivider(
                                                    modifier = Modifier.padding(start = 19.dp, end = 16.dp),
                                                    color = MaterialTheme.colorScheme.outlineVariant,
                                                )
                                            }
                                        }
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LogEntryRow(
    entry: RecentEntryUiState,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    var showMenu by remember { mutableStateOf(false) }

    Row(Modifier.height(IntrinsicSize.Min)) {
        // Category color left strip
        Box(
            Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(entry.categoryColor ?: Color.Transparent)
        )

        Row(
            modifier = Modifier
                .weight(1f)
                .padding(start = 13.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
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
                    entry.user,
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
                        onClick = {
                            showMenu = false
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}

private val dayFmt = DateTimeFormatter.ofPattern("EEEE, MMM d")
private val dayFmtYear = DateTimeFormatter.ofPattern("EEEE, MMM d, yyyy")
private fun formatDayHeader(iso: String): String = try {
    val d = LocalDate.parse(iso)
    val fmt = if (d.year == LocalDate.now().year) dayFmt else dayFmtYear
    d.format(fmt)
} catch (_: Exception) {
    iso
}
