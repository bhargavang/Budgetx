package com.budgetx.ai.feign;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** From budget-service's internal summary endpoint. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BudgetSummary(
        int month,
        int year,
        BigDecimal monthlySalary,
        BigDecimal totalEmi,
        BigDecimal spendingEstimate,
        BigDecimal disposableIncome,
        BigDecimal investableAmount,
        boolean overspent,
        BigDecimal emiToIncomePercent) {
}
