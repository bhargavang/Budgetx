package com.budgetx.expense.dto;

import java.math.BigDecimal;
import java.util.List;

public record MonthSummaryResponse(
        int month,
        int year,
        BigDecimal total,
        BigDecimal essentialTotal,
        BigDecimal discretionaryTotal,
        List<CategorySummary> byCategory) {
}
