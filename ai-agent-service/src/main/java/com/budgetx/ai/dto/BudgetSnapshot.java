package com.budgetx.ai.dto;

import java.math.BigDecimal;

public record BudgetSnapshot(int month, int year, BigDecimal monthlySalary, BigDecimal totalEmi,
                             BigDecimal spendingEstimate, BigDecimal disposableIncome, BigDecimal investableAmount,
                             BigDecimal emergencyFundContribution, BigDecimal amountToInvest) {
}
