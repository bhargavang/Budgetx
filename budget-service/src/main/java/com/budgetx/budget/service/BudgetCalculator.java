package com.budgetx.budget.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.budgetx.budget.dto.BudgetSummary;
import com.budgetx.budget.feign.RiskProfile;

/** Pure budget maths, kept free of I/O so it is easy to test. */
@Component
public class BudgetCalculator {

    /** Share of what's left after EMIs and spending that is set aside to invest. The rest stays as cash buffer. */
    private static final Map<RiskProfile, BigDecimal> INVESTMENT_SHARE = new EnumMap<>(Map.of(
            RiskProfile.CONSERVATIVE, new BigDecimal("0.50"),
            RiskProfile.MODERATE, new BigDecimal("0.60"),
            RiskProfile.AGGRESSIVE, new BigDecimal("0.70")));

    public BudgetSummary calculate(Long userId, int month, int year, BigDecimal salary, RiskProfile risk,
                                   BigDecimal totalEmi, BigDecimal spentThisMonth, BigDecimal spentLastMonth) {
        salary = orZero(salary);
        totalEmi = orZero(totalEmi);
        spentThisMonth = orZero(spentThisMonth);
        spentLastMonth = orZero(spentLastMonth);
        RiskProfile profile = risk == null ? RiskProfile.MODERATE : risk;

        BigDecimal estimate = spentThisMonth.max(spentLastMonth);
        BigDecimal disposable = salary.subtract(totalEmi).subtract(estimate);
        BigDecimal share = INVESTMENT_SHARE.get(profile);
        BigDecimal investable = disposable.signum() > 0
                ? disposable.multiply(share).setScale(0, RoundingMode.DOWN)
                : BigDecimal.ZERO;
        boolean overspent = totalEmi.add(spentThisMonth).compareTo(salary) > 0;
        BigDecimal emiRatio = salary.signum() > 0
                ? totalEmi.multiply(BigDecimal.valueOf(100)).divide(salary, 1, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        return new BudgetSummary(userId, month, year, salary, profile, totalEmi, spentThisMonth, spentLastMonth,
                estimate, disposable, share, investable, overspent, emiRatio);
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
