package com.budgetx.emi.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class EmiCalculatorTest {

    @Test
    void homeLoanInstalmentMatchesBankFigure() {
        // ₹10,00,000 at 8.5% for 20 years: banks quote ₹8,678.
        BigDecimal emi = EmiCalculator.monthlyInstalment(new BigDecimal("1000000"), new BigDecimal("8.5"), 240);

        assertThat(emi).isEqualByComparingTo("8678.23");
    }

    @Test
    void zeroInterestSplitsPrincipalEvenly() {
        assertThat(EmiCalculator.monthlyInstalment(new BigDecimal("60000"), BigDecimal.ZERO, 12))
                .isEqualByComparingTo("5000.00");
    }

    @Test
    void outstandingPrincipalReachesZeroAtEndOfTenure() {
        BigDecimal p = new BigDecimal("500000");
        BigDecimal rate = new BigDecimal("10.5");
        BigDecimal emi = EmiCalculator.monthlyInstalment(p, rate, 60);

        assertThat(EmiCalculator.outstandingPrincipal(p, rate, emi, 0)).isEqualByComparingTo("500000.00");
        assertThat(EmiCalculator.outstandingPrincipal(p, rate, emi, 30)).isBetween(
                new BigDecimal("250000"), new BigDecimal("300000"));
        assertThat(EmiCalculator.outstandingPrincipal(p, rate, emi, 60)).isLessThan(new BigDecimal("1"));
    }

    @Test
    void instalmentsPaidCountsStartMonthAndCapsAtTenure() {
        LocalDate start = LocalDate.of(2026, 1, 5);

        assertThat(EmiCalculator.instalmentsPaid(start, 12, LocalDate.of(2025, 12, 31))).isZero();
        assertThat(EmiCalculator.instalmentsPaid(start, 12, LocalDate.of(2026, 1, 5))).isEqualTo(1);
        assertThat(EmiCalculator.instalmentsPaid(start, 12, LocalDate.of(2026, 3, 10))).isEqualTo(3);
        assertThat(EmiCalculator.instalmentsPaid(start, 12, LocalDate.of(2030, 1, 1))).isEqualTo(12);
    }
}
