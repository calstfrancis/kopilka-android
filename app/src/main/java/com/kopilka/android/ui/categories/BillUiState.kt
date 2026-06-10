package com.kopilka.android.ui.categories

import com.kopilka.android.data.model.BudgetJson
import com.kopilka.android.data.model.FixedExpenseJson
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class BillUiState(
    val id: String,
    val name: String,
    val amount: Double,
    val dueDate: LocalDate,
    val dueDateLabel: String,
)

private val billDateFmt = DateTimeFormatter.ofPattern("EEE MMM d")

fun buildUpcomingBills(budget: BudgetJson, today: LocalDate = LocalDate.now(), lookAheadDays: Int = 14): List<BillUiState> {
    val cutoff = today.plusDays(lookAheadDays.toLong())
    return budget.expensesFixed
        .filter { it.active }
        .mapNotNull { expense ->
            val nextDue = nextDueDate(expense, today) ?: return@mapNotNull null
            if (nextDue > cutoff) return@mapNotNull null
            BillUiState(
                id = expense.id,
                name = expense.name,
                amount = expense.amount,
                dueDate = nextDue,
                dueDateLabel = when (nextDue) {
                    today -> "Today"
                    today.plusDays(1) -> "Tomorrow"
                    else -> nextDue.format(billDateFmt)
                },
            )
        }
        .sortedBy { it.dueDate }
}

private fun nextDueDate(expense: FixedExpenseJson, today: LocalDate): LocalDate? {
    return when (expense.frequency.lowercase()) {
        "monthly" -> {
            val day = expense.dueDay.coerceIn(1, 28)
            val candidate = today.withDayOfMonth(day)
            if (candidate >= today) candidate else candidate.plusMonths(1)
        }
        "weekly" -> {
            // dueWeekday: 0=Mon..6=Sun (matches Java DayOfWeek.value - 1)
            if (expense.dueWeekday < 0) return null
            val targetDow = expense.dueWeekday + 1 // 1=Mon..7=Sun
            val todayDow = today.dayOfWeek.value
            val daysAhead = ((targetDow - todayDow + 7) % 7).let { if (it == 0) 7 else it }
            // if today IS the due weekday, return today (0 days ahead)
            val daysAheadFinal = ((targetDow - todayDow + 7) % 7)
            today.plusDays(daysAheadFinal.toLong())
        }
        "yearly" -> {
            if (expense.dueDoy <= 0) return null
            val candidate = LocalDate.ofYearDay(today.year, expense.dueDoy.coerceIn(1, 365))
            if (candidate >= today) candidate else candidate.plusYears(1)
        }
        "semesterly" -> {
            // Approximate: twice a year
            val day = expense.dueDay.coerceIn(1, 28)
            val m1 = today.withDayOfMonth(day)
            val m6 = m1.plusMonths(6)
            listOf(m1, m6).firstOrNull { it >= today } ?: m1.plusYears(1)
        }
        else -> null
    }
}
