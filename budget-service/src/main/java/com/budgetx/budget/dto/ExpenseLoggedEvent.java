package com.budgetx.budget.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Consumed from {@code budget.expense.logged} (published by expense-service). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ExpenseLoggedEvent(Long userId, Long expenseId, BigDecimal amount, String category, LocalDate expenseDate) {
}
