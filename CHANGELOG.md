# Changelog — kopilka-android

## v1.0.0 — 2026-06-10

First stable release. All major features complete.

### New features

**Category detail screen**
- Tap any category card on the home screen to navigate to a detail view
- Shows budget vs. spent summary card with animated progress bar
- Lists all entries for that category grouped by date, with edit/delete menu
- Entries sorted newest-first

**Bills strip (upcoming fixed expenses)**
- New typed `FixedExpenseJson` data class; `expensesFixed` is now fully deserialized
- Home screen shows a horizontal scrolling strip of bills due in the next 14 days
- Each bill card shows name, amount, and a friendly due-date label (Today / Tomorrow / Mon Jun 16)
- Supports monthly (due_day), weekly (due_weekday), yearly (due_doy), and semesterly frequencies

**Budget limit notifications**
- New `CHANNEL_BUDGET` notification channel ("Budget Alerts")
- `NotificationHelper.showBudgetAlert()` fires when any category newly crosses 80% of its budget
- Check runs automatically on every successful sync in `SyncManager.fetchBudget`

**6-month bar chart in spending log**
- Canvas-drawn `MonthlyBarChart` composable at the top of the spending log screen
- Rounded-rect bars proportional to monthly total; month labels below
- Follows the same visual language as `SpendingSparkline`

**Search / filter in spending log**
- `OutlinedTextField` search bar below the chart, above the period chips
- Case-insensitive match on description or category name
- Clears automatically when the period changes

**Savings goals screen**
- New typed `SavingsGoalJson` data class; `savingsGoals` is now fully deserialized
- New `SavingsScreen` + `SavingsViewModel` — accessible via "Goals" chip on the hero card
- Each goal card: name, current / target amounts, animated `LinearProgressIndicator`, remaining and optional target date

**Debt overview screen**
- New typed `DebtJson` data class; `debt` is now fully deserialized
- New `DebtScreen` + `DebtViewModel` — accessible via "Debt" chip on the hero card
- Total outstanding summary card + per-debt cards with balance, rate %, payment, frequency
- Notes field rendered when present

**Calculator number pad**
- Amount field in Add/Edit Spending replaced with a read-only display + `AmountPad` grid
- 3×4 grid: digits 1–9, 0, decimal point, backspace
- Prevents leading zeros; caps input at 10 characters
- No on-screen keyboard appears for the amount field

### Internal changes

- `BudgetJson` fields `expensesFixed`, `debt`, `savingsGoals` changed from `List<JsonObject>` to typed data classes
- `CategoriesScreenState` extended with `upcomingBills: List<BillUiState>`
- `SpendingLogUiState` extended with `searchQuery` and `monthlyTotals`
- New routes in `KopilkaNavGraph`: `category/{categoryId}`, `savings`, `debt`
- `CategoriesScreen` accepts `onCategoryClick`, `onSavingsClick`, `onDebtClick` callbacks
- `CategoryCard` is now a clickable `Card`

---

## v1.0.0-beta3

Previous release — bug fixes and widget improvements.

## v1.0.0-beta1

Initial Android release.
