package com.kopilka.android.domain

import androidx.compose.ui.graphics.Color
import com.kopilka.android.data.model.BudgetJson
import com.kopilka.android.data.model.CategoryJson
import com.kopilka.android.data.model.ONE_TIME_CATEGORY_ID
import com.kopilka.android.data.model.PERIOD_TO_MONTHLY
import com.kopilka.android.data.model.SpendingEntryJson
import java.time.LocalDate

data class CategoryUiState(
    val id: String,
    val name: String,
    val budgetAmount: Double,
    val budgetPeriod: String,
    val spent: Double,
    val color: Color?,
    /** Daily spend for the last 7 days, index 0 = 6 days ago, index 6 = today. */
    val dailySpend: List<Float> = emptyList(),
) {
    val progress: Float get() = if (budgetAmount > 0) (spent / budgetAmount).toFloat().coerceIn(0f, 1f) else 0f
    val isOverBudget: Boolean get() = spent > budgetAmount
    val periodLabel: String get() = SpendingPeriod.label(budgetPeriod)
}

data class RecentEntryUiState(
    val id: String,
    val categoryName: String,
    val categoryColor: Color?,
    val amount: Double,
    val description: String,
    val date: String,
    val user: String,
)

/**
 * Mirrors Python's budget_for_month(month) / period_factor.
 *
 * budget_amount is stored in period units (e.g. $/week for a weekly category).
 * monthly_overrides keys are month integers stored as strings ("1".."12").
 * To get the effective period budget for a given month:
 *   1. Convert base to monthly: budgetAmount * periodFactor
 *   2. Apply override if present (already in monthly units)
 *   3. Convert back to period units: monthly / periodFactor
 * When there's no override this collapses to budgetAmount unchanged.
 */
private fun CategoryJson.effectivePeriodBudget(month: Int): Double {
    val factor = PERIOD_TO_MONTHLY[budgetPeriod] ?: 1.0
    val basMonthly = budgetAmount * factor
    val monthly = monthlyOverrides[month.toString()] ?: basMonthly
    return if (factor > 0) monthly / factor else 0.0
}

fun buildCategoryStates(budget: BudgetJson, today: LocalDate = LocalDate.now()): List<CategoryUiState> {
    val currentMonth = today.monthValue
    return budget.categories.map { cat ->
        val range = SpendingPeriod.current(cat.budgetPeriod, today)
        val effectiveBudget = cat.effectivePeriodBudget(currentMonth)
        val spent = budget.spending
            .filter { it.categoryId == cat.id && LocalDate.parse(it.date) in range }
            .sumOf { it.amount }
        val dailySpend = (6 downTo 0).map { daysAgo ->
            val day = today.minusDays(daysAgo.toLong())
            budget.spending
                .filter { it.categoryId == cat.id && LocalDate.parse(it.date) == day }
                .sumOf { it.amount }.toFloat()
        }
        CategoryUiState(
            id = cat.id,
            name = cat.name,
            budgetAmount = effectiveBudget,
            budgetPeriod = cat.budgetPeriod,
            spent = spent,
            color = cat.color.toComposeColor(),
            dailySpend = dailySpend,
        )
    }
}

fun buildRecentEntries(budget: BudgetJson, limit: Int = 5): List<RecentEntryUiState> {
    val catById = budget.categories.associateBy { it.id }
    return budget.spending
        .sortedByDescending { it.date }
        .take(limit)
        .map { entry ->
            val isOneTime = entry.categoryId == ONE_TIME_CATEGORY_ID
            val cat = if (isOneTime) null else catById[entry.categoryId]
            RecentEntryUiState(
                id = entry.id,
                categoryName = if (isOneTime) "One-time" else cat?.name ?: "Unknown",
                categoryColor = cat?.color?.toComposeColor(),
                amount = entry.amount,
                description = entry.description,
                date = entry.date,
                user = entry.user,
            )
        }
}

data class RecurringUiState(
    val id: String,
    val name: String,
    val categoryId: String,
    val categoryName: String,
    val categoryColor: Color?,
    val amount: Double,
    val description: String,
    val user: String,
    val frequency: String,
)

fun buildRecurringEntries(budget: BudgetJson): List<RecurringUiState> {
    val catById = budget.categories.associateBy { it.id }
    return budget.recurring
        .filter { it.active }
        .map { rec ->
            val cat = catById[rec.categoryId]
            RecurringUiState(
                id = rec.id,
                name = rec.name,
                categoryId = rec.categoryId,
                categoryName = cat?.name ?: "Unknown",
                categoryColor = cat?.color?.toComposeColor(),
                amount = rec.amount,
                description = rec.description,
                user = rec.user,
                frequency = rec.frequency,
            )
        }
}

internal fun String.toComposeColor(): Color? {
    if (length != 7 || !startsWith("#")) return null
    return try {
        val v = removePrefix("#").toLong(16) or 0xFF000000
        Color(v.toInt())
    } catch (_: NumberFormatException) {
        null
    }
}
