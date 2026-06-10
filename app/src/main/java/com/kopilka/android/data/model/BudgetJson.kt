package com.kopilka.android.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class BudgetJson(
    val version: String = "0.1.0",
    val metadata: MetadataJson = MetadataJson(),
    val config: ConfigJson = ConfigJson(),
    val income: List<JsonObject> = emptyList(),
    @SerialName("expenses_fixed") val expensesFixed: List<JsonObject> = emptyList(),
    val debt: List<JsonObject> = emptyList(),
    val categories: List<CategoryJson> = emptyList(),
    val spending: List<SpendingEntryJson> = emptyList(),
    @SerialName("savings_goals") val savingsGoals: List<JsonObject> = emptyList(),
    val assets: List<JsonObject> = emptyList(),
    val recurring: List<RecurringEntryJson> = emptyList(),
)

@Serializable
data class MetadataJson(
    val couple: List<String> = listOf("User 1", "User 2"),
    val created: String = "",
    @SerialName("last_modified") val lastModified: String = "",
    @SerialName("last_modified_by") val lastModifiedBy: String = "",
)

@Serializable
data class ConfigJson(
    val currency: String = "CAD",
    @SerialName("tax_year") val taxYear: Int = 2026,
    val province: String = "Nova Scotia",
    @SerialName("sync_path") val syncPath: String = "",
    @SerialName("bills_look_ahead_days") val billsLookAheadDays: Int = 7,
)

@Serializable
data class CategoryJson(
    val id: String,
    val name: String,
    @SerialName("budget_amount") val budgetAmount: Double,
    @SerialName("budget_period") val budgetPeriod: String = "weekly",
    val shared: Boolean = true,
    @SerialName("surplus_policy") val surplusPolicy: String = "ignore",
    @SerialName("deficit_policy") val deficitPolicy: String = "ignore",
    @SerialName("deficit_amortize_cycles") val deficitAmortizeCycles: Int = 3,
    @SerialName("monthly_overrides") val monthlyOverrides: Map<String, Double> = emptyMap(),
    val color: String = "",
)

@Serializable
data class SpendingEntryJson(
    val id: String,
    val date: String,
    @SerialName("category_id") val categoryId: String,
    val amount: Double,
    val description: String,
    val user: String,
)

@Serializable
data class RecurringEntryJson(
    val id: String,
    val name: String,
    @SerialName("category_id") val categoryId: String,
    val amount: Double,
    val description: String,
    val user: String,
    val frequency: String,
    @SerialName("next_date") val nextDate: String,
    val active: Boolean = true,
)

// Sentinel category_id for one-time / irregular purchases — matches the desktop app
const val ONE_TIME_CATEGORY_ID = "__one_time__"

// Period multipliers: period_amount × factor = monthly_amount
val PERIOD_TO_MONTHLY = mapOf(
    "weekly" to 4.33,
    "monthly" to 1.0,
    "semesterly" to 2.0 / 12.0,
    "yearly" to 1.0 / 12.0,
)
