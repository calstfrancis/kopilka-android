package com.kopilka.android.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.kopilka.android.MainActivity
import com.kopilka.android.R
import com.kopilka.android.data.model.PERIOD_TO_MONTHLY
import com.kopilka.android.data.storage.BudgetCache
import com.kopilka.android.domain.SpendingPeriod
import java.time.LocalDate
import kotlin.math.roundToInt

class KopilkaSummaryWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        manager: AppWidgetManager,
        widgetIds: IntArray,
    ) {
        val budget = BudgetCache(context).load()
        val today = LocalDate.now()

        widgetIds.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.widget_summary)

            val addIntent = Intent(context, MainActivity::class.java).apply {
                putExtra(EXTRA_DIRECT_ADD, true)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val addPi = PendingIntent.getActivity(
                context, id + 1000, addIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val openPi = PendingIntent.getActivity(
                context, id + 2000,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            views.setOnClickPendingIntent(R.id.widget_summary_add_btn, addPi)
            views.setOnClickPendingIntent(R.id.widget_spent_text, openPi)
            views.setOnClickPendingIntent(R.id.widget_budget_text, openPi)

            if (budget == null) {
                views.setTextViewText(R.id.widget_status_text, "Open Kopilka to sync")
                views.setProgressBar(R.id.widget_progress, 100, 0, false)
                manager.updateAppWidget(id, views)
                return@forEach
            }

            // Aggregate all categories using their own period windows
            var totalSpent = 0.0
            var totalBudget = 0.0
            var overBudgetCount = 0
            var worstCategory = ""
            var worstOverage = 0.0

            val currentMonth = today.monthValue
            budget.categories.forEach { cat ->
                val factor = PERIOD_TO_MONTHLY[cat.budgetPeriod] ?: 1.0
                val baseMonthly = cat.budgetAmount * factor
                val monthly = cat.monthlyOverrides[currentMonth.toString()] ?: baseMonthly
                val effectiveBudget = if (factor > 0) monthly / factor else 0.0

                val range = SpendingPeriod.current(cat.budgetPeriod, today)
                val spent = budget.spending
                    .filter { entry ->
                        entry.categoryId == cat.id &&
                            try { LocalDate.parse(entry.date) in range } catch (_: Exception) { false }
                    }
                    .sumOf { it.amount }

                totalSpent += spent
                totalBudget += effectiveBudget

                if (spent > effectiveBudget && effectiveBudget > 0) {
                    overBudgetCount++
                    val overage = spent - effectiveBudget
                    if (overage > worstOverage) {
                        worstOverage = overage
                        worstCategory = cat.name
                    }
                }
            }

            // Dominant period label (most common period among categories)
            val dominantPeriod = budget.categories
                .groupingBy { it.budgetPeriod }
                .eachCount()
                .maxByOrNull { it.value }
                ?.key ?: "weekly"
            val periodLabel = when (dominantPeriod) {
                "weekly" -> "This Week"
                "monthly" -> "This Month"
                "yearly" -> "This Year"
                "semesterly" -> "This Term"
                else -> "Current Period"
            }

            val progressPct = if (totalBudget > 0)
                ((totalSpent / totalBudget) * 100).roundToInt().coerceIn(0, 100) else 0
            val isOverBudget = totalBudget > 0 && totalSpent > totalBudget

            val statusText = when {
                totalSpent == 0.0 -> "No spending logged yet"
                overBudgetCount == 0 -> "All categories on track"
                overBudgetCount == 1 -> "$worstCategory over by \$${worstOverage.roundToInt()}"
                else -> "$overBudgetCount categories over budget"
            }

            views.setTextViewText(R.id.widget_period_label, periodLabel)
            views.setTextViewText(R.id.widget_spent_text, "\$${totalSpent.roundToInt()}")
            views.setTextViewText(R.id.widget_budget_text, " / \$${totalBudget.roundToInt()}")
            views.setProgressBar(R.id.widget_progress, 100, progressPct, false)
            views.setTextViewText(R.id.widget_status_text, statusText)
            // Color status text red when over budget (avoid transparent 0x00000000)
            views.setTextColor(
                R.id.widget_status_text,
                if (isOverBudget) 0xFFEE4444.toInt() else 0xFF9090B0.toInt(),
            )

            manager.updateAppWidget(id, views)
        }
    }
}
