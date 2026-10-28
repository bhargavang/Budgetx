package com.budgetx.user.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.budgetx.user.entity.Gender;
import com.budgetx.user.entity.RiskProfile;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Partial update: null fields are left unchanged. */
public record UpdateUserRequest(
        @Size(min = 1, max = 100) String fullName,
        @Pattern(regexp = "^\\+?[0-9]{10,14}$", message = "must be 10-14 digits, optionally starting with +") String phone,
        @Past LocalDate dateOfBirth,
        Gender gender,
        @Size(max = 100) String occupation,
        @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal monthlySalary,
        RiskProfile riskProfile) {
}
