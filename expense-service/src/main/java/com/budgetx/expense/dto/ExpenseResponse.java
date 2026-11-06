package com.budgetx.expense.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.budgetx.expense.entity.Expense;
import com.budgetx.expense.entity.PaymentMethod;

public record ExpenseResponse(
        Long expenseId,
        Long categoryId,
        String categoryName,
        BigDecimal amount,
        String description,
        LocalDate expenseDate,
        PaymentMethod paymentMethod,
        String note) {

    public static ExpenseResponse from(Expense expense) {
        return new ExpenseResponse(
                expense.getExpenseId(),
                expense.getCategory().getCategoryId(),
                expense.getCategory().getName(),
                expense.getAmount(),
                expense.getDescription(),
                expense.getExpenseDate(),
                expense.getPaymentMethod(),
                expense.getNote());
    }
}
