package com.budgetx.ai.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.budgetx.ai.rules.InvestmentType;
import com.budgetx.ai.rules.Share;

class AiPlanValidatorTest {

    private final AiPlanValidator validator = new AiPlanValidator();

    private static AiPlan plan(String summary, AiAllocation... items) {
        return new AiPlan(List.of(items), summary);
    }

    private static AiAllocation a(InvestmentType t, int pct, String reason) {
        return new AiAllocation(t, pct, reason);
    }

    @Test
    void acceptsAPlanWithinTheRules() {
        var r = validator.validate(plan("Grow slowly and keep safe money aside.",
                a(InvestmentType.INDEX_FUND_SIP, 45, "You are 28 with a moderate profile, so long-term growth fits."),
                a(InvestmentType.PPF, 30, "A safe base for retirement."),
                a(InvestmentType.GOLD, 10, "Balances market swings."),
                a(InvestmentType.FIXED_DEPOSIT, 15, "Money for goals in two or three years.")), 50);

        assertThat(r.usable()).isTrue();
        assertThat(r.violations()).isEmpty();
        assertThat(r.summary()).isEqualTo("Grow slowly and keep safe money aside.");
        assertThat(r.shares().stream().mapToInt(Share::percent).sum()).isEqualTo(100);
    }

    @Test
    void rejectsEquityOverTheCap() {
        var r = validator.validate(plan(null,
                a(InvestmentType.INDEX_FUND_SIP, 60, "Growth."),
                a(InvestmentType.PPF, 40, "Safety.")), 50);

        assertThat(r.usable()).isFalse();
        assertThat(r.violations()).anyMatch(v -> v.contains("equity 60%"));
    }

    @Test
    void rejectsTooMuchGold() {
        var r = validator.validate(plan(null,
                a(InvestmentType.INDEX_FUND_SIP, 30, "Growth."),
                a(InvestmentType.GOLD, 40, "Gold is shining."),
                a(InvestmentType.PPF, 30, "Safety.")), 50);

        assertThat(r.usable()).isFalse();
    }

    @Test
    void rejectsWhenPercentagesDontAddUp() {
        assertThat(validator.validate(plan(null,
                a(InvestmentType.INDEX_FUND_SIP, 30, "Growth."),
                a(InvestmentType.PPF, 30, "Safety.")), 50).usable()).isFalse();
        assertThat(validator.validate(plan(null), 50).usable()).isFalse();
        assertThat(validator.validate(null, 50).usable()).isFalse();
    }

    @Test
    void absorbsSmallRoundingInADebtSliceNotEquity() {
        var r = validator.validate(plan(null,
                a(InvestmentType.INDEX_FUND_SIP, 50, "Growth."),
                a(InvestmentType.PPF, 33, "Safety."),
                a(InvestmentType.FIXED_DEPOSIT, 16, "Near-term goals.")), 50);

        assertThat(r.usable()).isTrue();
        assertThat(r.shares()).filteredOn(s -> s.type() == InvestmentType.INDEX_FUND_SIP)
                .singleElement().extracting(Share::percent).isEqualTo(50);
        assertThat(r.shares().stream().mapToInt(Share::percent).sum()).isEqualTo(100);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Put some into Bitcoin for upside.",
            "Try F&O for quick gains.",
            "Buy penny stocks with high potential.",
            "Guaranteed safe returns.",
            "Use the HDFC Nifty 50 index fund.",
            "Open an account at zerodha.com to start.",
            "See https://example.com/ref=123 for details.",
            "Index funds have given 12% returns historically.",
            "PPF pays 7.1% p.a. right now.",
            "A ULIP gives insurance and investment together.",
    })
    void dropsSlicesWhoseReasonBreaksTheRules(String reason) {
        var r = validator.validate(plan(null,
                a(InvestmentType.INDEX_FUND_SIP, 40, reason),
                a(InvestmentType.PPF, 60, "A safe base.")), 50);

        // the bad slice is removed, the rest no longer adds up to 100, so the whole plan falls back to rules
        assertThat(r.usable()).isFalse();
        assertThat(r.violations()).anyMatch(v -> v.startsWith("INDEX_FUND_SIP: reason"));
    }

    @Test
    void keepsPlanButDropsABadSummary() {
        var r = validator.validate(plan("This is risk-free money.",
                a(InvestmentType.INDEX_FUND_SIP, 50, "Growth over 10 years."),
                a(InvestmentType.PPF, 50, "A safe base.")), 50);

        assertThat(r.usable()).isTrue();
        assertThat(r.summary()).isNull();
        assertThat(r.violations()).anyMatch(v -> v.startsWith("summary"));
    }

    @Test
    void ordinaryWordsAreNotMistakenForBrands() {
        assertThat(AiPlanValidator.problemWith(
                "You have options. Navigate market ups and downs with a 40% share of equity in a monthly SIP."))
                .isNull();
    }
}
