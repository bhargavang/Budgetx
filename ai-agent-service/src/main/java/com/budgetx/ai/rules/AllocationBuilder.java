package com.budgetx.ai.rules;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.budgetx.ai.dto.Allocation;

/** Turns validated percentages into rupee amounts, applying limits such as the PPF yearly cap. */
@Component
public class AllocationBuilder {

    /** PPF accepts at most ₹1.5 lakh a year. */
    static final BigDecimal PPF_MONTHLY_LIMIT = new BigDecimal("12500");

    public List<Allocation> build(List<Share> shares, BigDecimal amount) {
        Map<InvestmentType, BigDecimal> amounts = new LinkedHashMap<>();
        Map<InvestmentType, String> reasons = new LinkedHashMap<>();
        BigDecimal assigned = BigDecimal.ZERO;
        Share largest = null;
        for (Share s : shares) {
            BigDecimal a = amount.multiply(BigDecimal.valueOf(s.percent()))
                    .divide(BigDecimal.valueOf(100), 0, RoundingMode.DOWN);
            amounts.merge(s.type(), a, BigDecimal::add);
            reasons.putIfAbsent(s.type(), s.reason());
            assigned = assigned.add(a);
            // Prefer a debt slice for the rounding remainder so equity never grows past its share.
            boolean debt = s.type().assetClass() == AssetClass.DEBT;
            boolean largestDebt = largest != null && largest.type().assetClass() == AssetClass.DEBT;
            if (largest == null || (debt && !largestDebt) || (debt == largestDebt && s.percent() > largest.percent())) {
                largest = s;
            }
        }
        if (largest != null) { // rounding remainder (a few rupees)
            amounts.merge(largest.type(), amount.subtract(assigned), BigDecimal::add);
        }

        BigDecimal ppf = amounts.get(InvestmentType.PPF);
        if (ppf != null && ppf.compareTo(PPF_MONTHLY_LIMIT) > 0) {
            amounts.put(InvestmentType.PPF, PPF_MONTHLY_LIMIT);
            amounts.merge(InvestmentType.FIXED_DEPOSIT, ppf.subtract(PPF_MONTHLY_LIMIT), BigDecimal::add);
            reasons.putIfAbsent(InvestmentType.FIXED_DEPOSIT,
                    "PPF takes at most ₹1.5 lakh a year, so the rest of the safe portion goes here.");
        }

        List<Allocation> result = new ArrayList<>();
        amounts.forEach((type, a) -> {
            if (a.signum() <= 0) {
                return;
            }
            int pct = amount.signum() == 0 ? 0
                    : a.multiply(BigDecimal.valueOf(100)).divide(amount, 0, RoundingMode.HALF_UP).intValue();
            result.add(new Allocation(type, type.title(), type.assetClass(), type.riskLevel(), type.horizon(), pct, a,
                    type.about(), reasons.get(type)));
        });
        return result;
    }
}
