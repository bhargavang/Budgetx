package com.budgetx.ai.feign;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** From user-service's internal profile endpoint. Name and email are deliberately not read. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UserProfile(Long userId, Integer age, BigDecimal monthlySalary, RiskProfile riskProfile) {
}
