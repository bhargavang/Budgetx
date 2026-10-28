package com.budgetx.expense.dto;

import java.math.BigDecimal;

public record CategorySummary(Long categoryId, String categoryName, boolean essential, BigDecimal total, long count) {
}
