package com.budgetx.user.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.budgetx.user.entity.Gender;
import com.budgetx.user.entity.RiskProfile;
import com.budgetx.user.entity.User;

public record UserResponse(
        Long userId,
        String fullName,
        String email,
        String phone,
        LocalDate dateOfBirth,
        Gender gender,
        String occupation,
        BigDecimal monthlySalary,
        RiskProfile riskProfile,
        LocalDateTime createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getUserId(),
                user.getFullName(),
                user.getEmail(),
                user.getPhone(),
                user.getDateOfBirth(),
                user.getGender(),
                user.getOccupation(),
                user.getMonthlySalary(),
                user.getRiskProfile(),
                user.getCreatedAt());
    }
}
