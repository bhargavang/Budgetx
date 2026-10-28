package com.budgetx.expense.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.budgetx.expense.entity.PaymentMethod;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

public record ExpenseRequest(
        @NotNull Long categoryId,
        @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal amount,
        @Size(max = 255) String description,
        @NotNull @PastOrPresent LocalDate expenseDate,
        PaymentMethod paymentMethod) {
}
