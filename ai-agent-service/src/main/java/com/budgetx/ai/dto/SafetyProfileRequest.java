package com.budgetx.ai.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** What the user tells us about their safety net. We can't see bank balances or policies, so we ask. */
public record SafetyProfileRequest(
        @NotNull @DecimalMin("0.00") @Digits(integer = 12, fraction = 2) BigDecimal emergencySavings,
        @NotNull @Min(0) @Max(20) Integer dependents,
        @NotNull Boolean hasTermInsurance,
        @NotNull Boolean hasHealthInsurance) {
}
