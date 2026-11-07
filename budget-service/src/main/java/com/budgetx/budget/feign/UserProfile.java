package com.budgetx.budget.feign;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** The fields budget-service needs from user-service's internal profile endpoint. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UserProfile(Long userId, BigDecimal monthlySalary, RiskProfile riskProfile) {
}
