package com.budgetx.expense.dto;

import java.math.BigDecimal;

import com.budgetx.expense.entity.MonthlyExpenseType;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** type defaults to OTHER and active to true when left out. */
public record MonthlyExpenseRequest(
        @NotBlank @Size(max = 100) String name,
        @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal amount,
        MonthlyExpenseType type,
        @Min(1) @Max(31) Integer dueDay,
        @Size(max = 500) String note,
        Boolean active) {
}
