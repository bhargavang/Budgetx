package com.budgetx.ai.rules;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.budgetx.ai.dto.ActionItem;
import com.budgetx.ai.dto.Insight;
import com.budgetx.ai.entity.SafetyNet;
import com.budgetx.ai.feign.BudgetSummary;
import com.budgetx.ai.feign.RiskProfile;
import com.budgetx.ai.feign.UserProfile;

class FinancialRuleEngineTest {

    private final FinancialRuleEngine engine = new FinancialRuleEngine();

    static BudgetSummary budget(String salary, String emi, String spending, String investable, boolean overspent) {
        BigDecimal s = new BigDecimal(salary);
        BigDecimal e = new BigDecimal(emi);
        BigDecimal sp = new BigDecimal(spending);
        BigDecimal ratio = s.signum() == 0 ? BigDecimal.ZERO
                : e.multiply(BigDecimal.valueOf(100)).divide(s, 1, java.math.RoundingMode.HALF_UP);
        return new BudgetSummary(10, 2026, s, e, sp, s.subtract(e).subtract(sp), new BigDecimal(investable), overspent, ratio);
    }

    static SafetyNet safety(String savings, int dependents, boolean term, boolean health) {
        return SafetyNet.builder().userId(1L).emergencySavings(new BigDecimal(savings)).dependents(dependents)
                .hasTermInsurance(term).hasHealthInsurance(health).updatedAt(LocalDateTime.now()).build();
    }

    private static boolean hasAction(RuleOutcome o, String type) {
        return o.actions().stream().map(ActionItem::type).anyMatch(type::equals);
    }

    private static boolean hasWarning(RuleOutcome o, String severity, String titlePart) {
        return o.warnings().stream().filter(w -> w.severity().equals(severity)).map(Insight::title)
                .anyMatch(t -> t.contains(titlePart));
    }

    @Test
    void equityCapFollowsRiskProfileAndAge() {
        assertThat(FinancialRuleEngine.equityCap(RiskProfile.CONSERVATIVE, 30)).isEqualTo(30);
        assertThat(FinancialRuleEngine.equityCap(RiskProfile.MODERATE, null)).isEqualTo(50);
        assertThat(FinancialRuleEngine.equityCap(RiskProfile.AGGRESSIVE, 25)).isEqualTo(70);
        assertThat(FinancialRuleEngine.equityCap(RiskProfile.AGGRESSIVE, 60)).isEqualTo(40);
        assertThat(FinancialRuleEngine.equityCap(null, 30)).isEqualTo(50);
    }

    @Test
    void overspendingBlocksAllInvesting() {
        RuleOutcome o = engine.apply(new UserProfile(1L, 30, new BigDecimal("40000"), RiskProfile.AGGRESSIVE),
                budget("40000", "25000", "20000", "0", true), safety("0", 0, false, true));

        assertThat(hasWarning(o, "CRITICAL", "spending more than you earn")).isTrue();
        assertThat(o.amountToInvest()).isEqualByComparingTo("0");
        assertThat(o.canInvest()).isFalse();
    }

    @Test
    void emergencyFundTakesMostOfTheSavingsWhenThereIsNone() {
        RuleOutcome o = engine.apply(new UserProfile(1L, 30, new BigDecimal("100000"), RiskProfile.MODERATE),
                budget("100000", "10000", "40000", "30000", false), safety("0", 0, true, true));

        assertThat(o.emergencyFundTarget()).isEqualByComparingTo("300000");
        assertThat(o.emergencyFundContribution()).isEqualByComparingTo("21000");
        assertThat(o.amountToInvest()).isEqualByComparingTo("9000");
        assertThat(hasAction(o, "EMERGENCY_FUND")).isTrue();
    }

    @Test
    void fullEmergencyFundAndInsuranceMeansEverythingCanBeInvested() {
        RuleOutcome o = engine.apply(new UserProfile(1L, 30, new BigDecimal("100000"), RiskProfile.MODERATE),
                budget("100000", "10000", "40000", "30000", false), safety("400000", 2, true, true));

        assertThat(o.emergencyFundContribution()).isEqualByComparingTo("0");
        assertThat(o.amountToInvest()).isEqualByComparingTo("30000");
        assertThat(o.actions()).isEmpty();
    }

    @Test
    void termInsuranceOnlyWhenSomeoneDependsOnYou() {
        UserProfile p = new UserProfile(1L, 35, new BigDecimal("60000"), RiskProfile.MODERATE);
        BudgetSummary b = budget("60000", "0", "30000", "18000", false);

        RuleOutcome withDependents = engine.apply(p, b, safety("500000", 2, false, true));
        RuleOutcome alone = engine.apply(p, b, safety("500000", 0, false, true));

        assertThat(hasAction(withDependents, "TERM_INSURANCE")).isTrue();
        assertThat(withDependents.actions().get(0).detail()).contains("72,00,000").contains("Avoid endowment");
        assertThat(hasAction(alone, "TERM_INSURANCE")).isFalse();
        assertThat(hasWarning(alone, "INFO", "Term insurance can wait")).isTrue();
    }

    @Test
    void healthInsuranceAndSafetyQuestionsWhenProfileMissing() {
        RuleOutcome o = engine.apply(new UserProfile(1L, null, new BigDecimal("50000"), RiskProfile.MODERATE),
                budget("50000", "0", "20000", "18000", false), null);

        assertThat(hasAction(o, "HEALTH_INSURANCE")).isTrue();
        assertThat(hasAction(o, "EMERGENCY_FUND")).isTrue();
        assertThat(hasWarning(o, "INFO", "safety net")).isTrue();
    }

    @Test
    void heavyEmisAreFlagged() {
        RuleOutcome o = engine.apply(new UserProfile(1L, 40, new BigDecimal("50000"), RiskProfile.MODERATE),
                budget("50000", "28000", "10000", "7200", false), safety("300000", 1, true, true));

        assertThat(hasWarning(o, "CRITICAL", "56% of your salary")).isTrue();
        assertThat(hasAction(o, "DEBT")).isTrue();
    }

    @Test
    void formatsRupeesTheIndianWay() {
        assertThat(FinancialRuleEngine.inr(new BigDecimal("7200000"))).isEqualTo("₹72,00,000");
        assertThat(FinancialRuleEngine.inr(new BigDecimal("12345678"))).isEqualTo("₹1,23,45,678");
        assertThat(FinancialRuleEngine.inr(new BigDecimal("1000"))).isEqualTo("₹1,000");
        assertThat(FinancialRuleEngine.inr(new BigDecimal("999"))).isEqualTo("₹999");
    }

    @Test
    void zeroSalaryAsksForSalary() {
        RuleOutcome o = engine.apply(new UserProfile(1L, 30, BigDecimal.ZERO, RiskProfile.MODERATE),
                budget("0", "0", "0", "0", false), null);

        assertThat(hasWarning(o, "CRITICAL", "salary")).isTrue();
        assertThat(o.canInvest()).isFalse();
    }
}
