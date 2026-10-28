package com.budgetx.expense.dto;

import java.math.BigDecimal;

public record MonthlySummary(int month, BigDecimal total, long count) {
}
