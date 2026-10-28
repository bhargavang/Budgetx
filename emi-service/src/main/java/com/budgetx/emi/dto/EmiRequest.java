package com.budgetx.emi.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.budgetx.emi.entity.LoanType;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * {@code emiAmount} is optional: when omitted it is computed from principal, rate and tenure.
 * Pass it explicitly when the lender's figure differs (processing fees, rounding).
 */
public record EmiRequest(
        @NotBlank @Size(max = 100) String loanName,
        @Size(max = 100) String lenderName,
        @NotNull LoanType loanType,
        @NotNull @DecimalMin(value = "0.01") @Digits(integer = 12, fraction = 2) BigDecimal principalAmount,
        @NotNull @DecimalMin("0.00") @DecimalMax("60.00") @Digits(integer = 3, fraction = 2) BigDecimal interestRate,
        @NotNull @Min(1) @Max(480) Integer tenureMonths,
        @DecimalMin(value = "0.01") @Digits(integer = 10, fraction = 2) BigDecimal emiAmount,
        @NotNull LocalDate startDate) {
}
