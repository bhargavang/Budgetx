package com.budgetx.user.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Period;

import com.budgetx.user.entity.RiskProfile;
import com.budgetx.user.entity.User;

/** Financial profile consumed by other services (budget, ai-agent) over Feign. */
public record UserProfileResponse(
        Long userId,
        String fullName,
        Integer age,
        String occupation,
        BigDecimal monthlySalary,
        RiskProfile riskProfile) {

    public static UserProfileResponse from(User user) {
        Integer age = user.getDateOfBirth() == null
                ? null
                : Period.between(user.getDateOfBirth(), LocalDate.now()).getYears();
        return new UserProfileResponse(
                user.getUserId(),
                user.getFullName(),
                age,
                user.getOccupation(),
                user.getMonthlySalary(),
                user.getRiskProfile());
    }
}
