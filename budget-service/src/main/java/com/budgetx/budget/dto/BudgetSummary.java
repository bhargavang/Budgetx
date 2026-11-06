package com.budgetx.budget.dto;

import java.math.BigDecimal;

import com.budgetx.budget.feign.RiskProfile;

/**
 * One month's budget for a user.
 *
 * @param spentThisMonth       expenses logged so far this month
 * @param spendingEstimate     the higher of this month so far and last month's total, so early in the
 *                             month the investable amount isn't inflated by spending not yet logged
 * @param disposableIncome     salary - EMIs - spendingEstimate
 * @param investmentPercentage share of a positive disposable income set aside to invest (by risk profile)
 * @param investableAmount     disposableIncome x investmentPercentage, never negative
 * @param overspent            this month's actual EMIs + expenses exceed the salary
 * @param emiToIncomePercent   EMIs as a percentage of salary (0 when salary is 0)
 */
public record BudgetSummary(
        Long userId,
        int month,
        int year,
        BigDecimal monthlySalary,
        RiskProfile riskProfile,
        BigDecimal totalEmi,
        BigDecimal spentThisMonth,
        BigDecimal spentLastMonth,
        BigDecimal spendingEstimate,
        BigDecimal disposableIncome,
        BigDecimal investmentPercentage,
        BigDecimal investableAmount,
        boolean overspent,
        BigDecimal emiToIncomePercent) {
}
