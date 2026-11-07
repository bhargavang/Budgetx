package com.budgetx.budget.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.budgetx.budget.dto.BudgetSummary;
import com.budgetx.budget.feign.RiskProfile;

class BudgetCalculatorTest {

    private final BudgetCalculator calculator = new BudgetCalculator();

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }

    @Test
    void investsShareOfDisposableIncomeByRiskProfile() {
        BudgetSummary b = calculator.calculate(1L, 10, 2026, bd("100000"), RiskProfile.MODERATE,
                bd("20000"), bd("30000"), bd("25000"));

        assertThat(b.spendingEstimate()).isEqualByComparingTo("30000");
        assertThat(b.disposableIncome()).isEqualByComparingTo("50000");
        assertThat(b.investableAmount()).isEqualByComparingTo("30000");
        assertThat(b.emiToIncomePercent()).isEqualByComparingTo("20.0");
        assertThat(b.overspent()).isFalse();
    }

    @Test
    void usesLastMonthWhenThisMonthHasLessLoggedSoFar() {
        BudgetSummary b = calculator.calculate(1L, 10, 2026, bd("50000"), RiskProfile.CONSERVATIVE,
                BigDecimal.ZERO, bd("2000"), bd("30000"));

        assertThat(b.spendingEstimate()).isEqualByComparingTo("30000");
        assertThat(b.investableAmount()).isEqualByComparingTo("10000");
    }

    @Test
    void nothingToInvestWhenOverspent() {
        BudgetSummary b = calculator.calculate(1L, 10, 2026, bd("40000"), RiskProfile.AGGRESSIVE,
                bd("25000"), bd("20000"), BigDecimal.ZERO);

        assertThat(b.overspent()).isTrue();
        assertThat(b.disposableIncome()).isEqualByComparingTo("-5000");
        assertThat(b.investableAmount()).isEqualByComparingTo("0");
    }

    @Test
    void handlesMissingValuesAndZeroSalary() {
        BudgetSummary b = calculator.calculate(1L, 10, 2026, null, null, null, null, null);

        assertThat(b.riskProfile()).isEqualTo(RiskProfile.MODERATE);
        assertThat(b.investableAmount()).isEqualByComparingTo("0");
        assertThat(b.emiToIncomePercent()).isEqualByComparingTo("0");
        assertThat(b.overspent()).isFalse();
    }
}
