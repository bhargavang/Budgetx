package com.budgetx.expense.dto;

import java.math.BigDecimal;
import java.util.List;

/** All of a user's monthly expenses; monthlyTotal and activeCount cover active ones only. */
public record MonthlyExpensesResponse(
        BigDecimal monthlyTotal,
        int activeCount,
        List<MonthlyExpenseResponse> items) {
}
