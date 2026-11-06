package com.budgetx.expense.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * One day of the salary-based tracker.
 *
 * <p>spendableThisMonth = monthlySalary - monthlyEmi - monthlySavingsGoal (never below zero).
 * dailyBudget spreads that evenly over the month. todaysAllowance re-spreads what is left at the
 * start of the day over the days remaining (this day included), so overspending earlier lowers it
 * and underspending raises it.
 */
public record DailySummaryResponse(
        LocalDate date,
        BigDecimal monthlySalary,
        BigDecimal monthlyEmi,
        boolean emiAvailable,
        BigDecimal monthlySavingsGoal,
        BigDecimal spendableThisMonth,
        int daysInMonth,
        int daysLeftInMonth,
        BigDecimal dailyBudget,
        BigDecimal todaysAllowance,
        BigDecimal spentToday,
        BigDecimal remainingToday,
        BigDecimal spentThisMonth,
        BigDecimal remainingThisMonth,
        String dayNote,
        List<ExpenseResponse> expenses) {
}
