package com.budgetx.expense.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonFormat;

/** Published to Kafka topic {@code budget.expense.logged} after an expense is saved. */
public record ExpenseEvent(
        Long userId,
        Long expenseId,
        BigDecimal amount,
        String category,
        @JsonFormat(shape = JsonFormat.Shape.STRING) LocalDate expenseDate) {
}
