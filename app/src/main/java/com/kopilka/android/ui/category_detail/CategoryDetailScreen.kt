package com.kopilka.android.ui.category_detail

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
fun CategoryDetailScreen(
    categoryId: String,
    myName: String,
    onBack: () -> Unit,
    onEditEntry: (String) -> Unit,
    vm: CategoryDetailViewModel = viewModel(),
) {
    LaunchedEffect(categoryId) { vm.load(categoryId) }
    val state by vm.state.collectAsStateWithLifecycle()

    val animatedProgress by animateFloatAsState(
        targetValue = state.progress,
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "cat_detail_progress",
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.categoryName.ifEmpty { "Category" }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.isLoading -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            state.error != null -> {
                Box(Modifier.fillMaxSize().padding(padding).padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(state.error!!, color = MaterialTheme.colorScheme.error)
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Summary card
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.extraLarge,
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                            ),
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Bottom,
                                ) {
                                    Column {
                                        Text(
                                            "Spent this period",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                                        )
                                        Text(
                                            "$${String.format("%.2f", state.spent)}",
                                            style = MaterialTheme.typography.headlineMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = if (state.isOverBudget)
                                                MaterialTheme.colorScheme.error
                                            else
                                                MaterialTheme.colorScheme.onPrimaryContainer,
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            "Budget",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                                        )
                                        Text(
                                            "$${String.format("%.2f", state.budgetAmount)} / ${state.budgetPeriod}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        )
                                    }
                                }

                                LinearProgressIndicator(
                                    progress = { animatedProgress },
                                    modifier = Modifier.fillMaxWidth().height(8.dp),
                                    color = if (state.isOverBudget)
                                        MaterialTheme.colorScheme.error
                                    else
                                        MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f),
                                )

                                if (state.isOverBudget) {
                                    Text(
                                        "Over by $${String.format("%.2f", state.spent - state.budgetAmount)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                } else if (state.budgetAmount > 0) {
                                    Text(
                                        "$${String.format("%.2f", state.budgetAmount - state.spent)} remaining",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                                    )
                                }
                            }
                        }
                    }

                    // Entries header
                    if (state.entries.isNotEmpty()) {
                        item {
                            Text(
                                "Entries",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 4.dp),
                            )
                        }

                        // Group by date
                        val grouped = state.entries.groupBy { it.date }
                        grouped.forEach { (date, dayEntries) ->
                            item(key = "header_$date") {
                                Text(
                                    formatDayHeader(date),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                                )
                            }
                            item(key = "card_$date") {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = MaterialTheme.shapes.large,
                                ) {
                                    Column {
                                        dayEntries.forEachIndexed { i, entry ->
                                            DetailEntryRow(
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
                            }
                        }
                    } else {
                        item {
                            Box(
                                Modifier.fillMaxWidth().padding(32.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "No entries for this category",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }
}

@Composable
private fun DetailEntryRow(
    entry: RecentEntryUiState,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    var showMenu by remember { mutableStateOf(false) }

    Row(Modifier.height(IntrinsicSize.Min)) {
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
                if (entry.description.isNotBlank()) {
                    Text(
                        entry.description,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
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
} catch (_: Exception) { iso }
