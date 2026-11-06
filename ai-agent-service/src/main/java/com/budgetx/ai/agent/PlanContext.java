package com.budgetx.ai.agent;

import java.math.BigDecimal;

import com.budgetx.ai.feign.RiskProfile;

/** The only facts sent to the AI: anonymous numbers, no name, email or ids. */
public record PlanContext(
        Integer age,
        RiskProfile riskProfile,
        BigDecimal monthlySalary,
        BigDecimal monthlyEmi,
        BigDecimal monthlySpending,
        BigDecimal amountToInvest,
        int equityCapPercent,
        int maxGoldPercent,
        int dependents,
        boolean emergencyFundComplete,
        boolean hasHealthInsurance,
        boolean hasTermInsurance) {
}
