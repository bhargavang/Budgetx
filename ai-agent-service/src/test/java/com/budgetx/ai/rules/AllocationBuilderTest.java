package com.budgetx.ai.rules;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.budgetx.ai.dto.Allocation;
import com.budgetx.ai.feign.RiskProfile;

class AllocationBuilderTest {

    private final AllocationBuilder builder = new AllocationBuilder();
    private final DefaultAllocator defaults = new DefaultAllocator();

    private static BigDecimal amount(List<Allocation> list, InvestmentType type) {
        return list.stream().filter(a -> a.type() == type).map(Allocation::monthlyAmount).findFirst().orElse(BigDecimal.ZERO);
    }

    @Test
    void defaultSplitRespectsEquityCapAndAddsUp() {
        List<Share> shares = defaults.allocate(RiskProfile.CONSERVATIVE, 30);
        assertThat(shares.stream().mapToInt(Share::percent).sum()).isEqualTo(100);

        List<Allocation> result = builder.build(shares, new BigDecimal("10001"));

        assertThat(amount(result, InvestmentType.INDEX_FUND_SIP)).isEqualByComparingTo("3000");
        assertThat(result.stream().map(Allocation::monthlyAmount).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("10001");
        assertThat(result).allSatisfy(a -> assertThat(a.title()).isEqualTo(a.type().title()));
    }

    @Test
    void ppfOverTheYearlyLimitSpillsIntoDeposits() {
        List<Allocation> result = builder.build(List.of(
                new Share(InvestmentType.INDEX_FUND_SIP, 50, "growth"),
                new Share(InvestmentType.PPF, 50, "safe")), new BigDecimal("40000"));

        assertThat(amount(result, InvestmentType.PPF)).isEqualByComparingTo("12500");
        assertThat(amount(result, InvestmentType.FIXED_DEPOSIT)).isEqualByComparingTo("7500");
    }

    @Test
    void zeroEquityCapMeansNoEquity() {
        List<Share> shares = defaults.allocate(RiskProfile.CONSERVATIVE, 0);

        assertThat(shares).noneMatch(s -> s.type() == InvestmentType.INDEX_FUND_SIP);
        assertThat(shares.stream().mapToInt(Share::percent).sum()).isEqualTo(100);
    }
}
