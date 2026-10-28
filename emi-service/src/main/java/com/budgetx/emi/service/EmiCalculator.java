package com.budgetx.emi.service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Standard reducing-balance loan maths. */
public final class EmiCalculator {

    private static final MathContext MC = MathContext.DECIMAL64;
    private static final BigDecimal TWELVE_HUNDRED = BigDecimal.valueOf(1200);

    private EmiCalculator() {
    }

    /** EMI = P * r * (1 + r)^n / ((1 + r)^n - 1), with r the monthly rate. */
    public static BigDecimal monthlyInstalment(BigDecimal principal, BigDecimal annualRatePercent, int months) {
        if (annualRatePercent.signum() == 0) {
            return principal.divide(BigDecimal.valueOf(months), 2, RoundingMode.HALF_UP);
        }
        BigDecimal r = annualRatePercent.divide(TWELVE_HUNDRED, MC);
        BigDecimal growth = BigDecimal.ONE.add(r).pow(months, MC);
        return principal.multiply(r, MC).multiply(growth, MC)
                .divide(growth.subtract(BigDecimal.ONE), MC)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /** Principal still owed after {@code paid} instalments. */
    public static BigDecimal outstandingPrincipal(BigDecimal principal, BigDecimal annualRatePercent,
                                                  BigDecimal emi, int paid) {
        if (paid <= 0) {
            return principal.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal remaining;
        if (annualRatePercent.signum() == 0) {
            remaining = principal.subtract(emi.multiply(BigDecimal.valueOf(paid)));
        } else {
            BigDecimal r = annualRatePercent.divide(TWELVE_HUNDRED, MC);
            BigDecimal growth = BigDecimal.ONE.add(r).pow(paid, MC);
            remaining = principal.multiply(growth, MC)
                    .subtract(emi.multiply(growth.subtract(BigDecimal.ONE), MC).divide(r, MC));
        }
        return remaining.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    /** Instalments paid by {@code asOf}, counting one per month from {@code startDate} inclusive. */
    public static int instalmentsPaid(LocalDate startDate, int tenureMonths, LocalDate asOf) {
        if (asOf.isBefore(startDate)) {
            return 0;
        }
        long paid = ChronoUnit.MONTHS.between(startDate, asOf) + 1;
        return (int) Math.min(paid, tenureMonths);
    }
}
