package com.kopilka.android.ui.categories

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kopilka.android.data.model.BudgetJson
import com.kopilka.android.data.model.PERIOD_TO_MONTHLY
import com.kopilka.android.domain.CategoryUiState
import com.kopilka.android.domain.RecurringUiState
import com.kopilka.android.domain.RecentEntryUiState
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// BillUiState defined in BillUiState.kt in same package

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(
    onOpenSetup: () -> Unit,
    onAddSpending: () -> Unit,
    onEditEntry: (String) -> Unit,
    onViewAll: () -> Unit,
    onCategoryClick: (String) -> Unit = {},
    onSavingsClick: () -> Unit = {},
    onDebtClick: () -> Unit = {},
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
                        badge = { if (state.pendingUpload) Badge() },
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

                        // Hero totals card
                        if (state.categories.isNotEmpty()) {
                            item {
                                HeroTotalsCard(
                                    categories = state.categories,
                                    budget = state.budget,
                                    onSavingsClick = onSavingsClick,
                                    onDebtClick = onDebtClick,
                                )
                            }
                        }

                        // Upcoming bills strip
                        if (state.upcomingBills.isNotEmpty()) {
                            item {
                                Text(
                                    "Upcoming",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 4.dp),
                                )
                            }
                            item {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp),
                                ) {
                                    state.upcomingBills.forEach { bill ->
                                        item(key = bill.id) {
                                            BillCard(bill)
                                        }
                                    }
                                }
                            }
                        }

                        // Category cards
                        state.categories.forEach { cat ->
                            item(key = cat.id) {
                                CategoryCard(cat, onClick = { onCategoryClick(cat.id) })
                            }
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
                                            RecurringEntryRow(rec = rec, onLog = { vm.quickLogRecurring(rec) })
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

                        // Recent entries with "See all"
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
                                                    modifier = Modifier.padding(start = 19.dp, end = 16.dp),
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
private fun HeroTotalsCard(
    categories: List<CategoryUiState>,
    budget: BudgetJson?,
    onSavingsClick: () -> Unit = {},
    onDebtClick: () -> Unit = {},
) {
    val today = LocalDate.now()
    val monthlySpent = budget?.spending?.mapNotNull { entry ->
        try {
            val d = LocalDate.parse(entry.date)
            if (d.year == today.year && d.monthValue == today.monthValue) entry.amount else null
        } catch (_: Exception) { null }
    }?.sum() ?: 0.0

    val monthlyBudget = categories.sumOf { cat ->
        cat.budgetAmount * (PERIOD_TO_MONTHLY[cat.budgetPeriod] ?: 1.0)
    }

    val progress = if (monthlyBudget > 0) (monthlySpent / monthlyBudget).toFloat().coerceIn(0f, 1.5f) else 0f
    val isOver = monthlySpent > monthlyBudget

    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "hero_progress",
    )

    val progressColor = if (isOver) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val strokeWidthDp = 14.dp
    val strokeWidthPx = with(LocalDensity.current) { strokeWidthDp.toPx() }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // Donut ring
            Canvas(Modifier.size(96.dp)) {
                val inset = strokeWidthPx / 2f
                val arcTopLeft = Offset(inset, inset)
                val arcSize = Size(size.width - strokeWidthPx, size.height - strokeWidthPx)
                drawArc(
                    color = trackColor, startAngle = -90f, sweepAngle = 360f, useCenter = false,
                    topLeft = arcTopLeft, size = arcSize,
                    style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round),
                )
                drawArc(
                    color = progressColor, startAngle = -90f, sweepAngle = animatedProgress * 360f, useCenter = false,
                    topLeft = arcTopLeft, size = arcSize,
                    style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round),
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "This month",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                )
                Text(
                    "$${String.format("%.2f", monthlySpent)}",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isOver) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    "of $${String.format("%.0f", monthlyBudget)} budget",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                )
                Spacer(Modifier.height(2.dp))
                if (isOver) {
                    Text(
                        "over by $${String.format("%.2f", monthlySpent - monthlyBudget)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold,
                    )
                } else if (monthlyBudget > 0) {
                    Text(
                        "${String.format("%.0f", (1f - animatedProgress) * 100)}% remaining",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f),
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = onSavingsClick,
                        label = { Text("Goals", style = MaterialTheme.typography.labelSmall) },
                    )
                    AssistChip(
                        onClick = onDebtClick,
                        label = { Text("Debt", style = MaterialTheme.typography.labelSmall) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryCard(cat: CategoryUiState, onClick: () -> Unit = {}) {
    val animatedProgress by animateFloatAsState(
        targetValue = cat.progress,
        animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
        label = "progress_${cat.id}",
    )

    val progressColor = when {
        cat.isOverBudget -> MaterialTheme.colorScheme.error
        cat.progress > 0.85f -> MaterialTheme.colorScheme.tertiary
        else -> cat.color ?: MaterialTheme.colorScheme.primary
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        onClick = onClick,
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            // Gradient left edge strip
            cat.color?.let { c ->
                Box(
                    Modifier
                        .width(5.dp)
                        .fillMaxHeight()
                        .background(
                            Brush.verticalGradient(listOf(c, c.copy(alpha = 0.35f)))
                        )
                )
            }

            Column(
                modifier = Modifier.padding(16.dp).weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
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

                LinearProgressIndicator(
                    progress = { animatedProgress },
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
}

@Composable
private fun RecurringEntryRow(rec: RecurringUiState, onLog: () -> Unit) {
    val haptic = LocalHapticFeedback.current

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
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onLog()
            },
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

@Composable
private fun BillCard(bill: BillUiState) {
    Card(
        modifier = Modifier.width(140.dp),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                bill.name,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "$${String.format("%.2f", bill.amount)}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                bill.dueDateLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val dateFmt = DateTimeFormatter.ofPattern("MMM d")
private fun formatDate(iso: String): String = try {
    LocalDate.parse(iso).format(dateFmt)
} catch (_: Exception) {
    iso
}
