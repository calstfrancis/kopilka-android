package com.kopilka.android.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

data class DateRange(val start: LocalDate, val end: LocalDate) {
    operator fun contains(date: LocalDate) = !date.isBefore(start) && !date.isAfter(end)
}

object SpendingPeriod {
    fun current(period: String, today: LocalDate = LocalDate.now()): DateRange = when (period) {
        "weekly" -> {
            val start = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            DateRange(start, start.plusDays(6))
        }
        "monthly" -> {
            val start = today.withDayOfMonth(1)
            DateRange(start, start.with(TemporalAdjusters.lastDayOfMonth()))
        }
        "semesterly" -> if (today.monthValue <= 6) {
            DateRange(LocalDate.of(today.year, 1, 1), LocalDate.of(today.year, 6, 30))
        } else {
            DateRange(LocalDate.of(today.year, 7, 1), LocalDate.of(today.year, 12, 31))
        }
        "yearly" -> DateRange(LocalDate.of(today.year, 1, 1), LocalDate.of(today.year, 12, 31))
        else -> {
            val start = today.withDayOfMonth(1)
            DateRange(start, start.with(TemporalAdjusters.lastDayOfMonth()))
        }
    }

    fun label(period: String): String = when (period) {
        "weekly" -> "this week"
        "monthly" -> "this month"
        "semesterly" -> "this semester"
        "yearly" -> "this year"
        else -> "this period"
    }
}
