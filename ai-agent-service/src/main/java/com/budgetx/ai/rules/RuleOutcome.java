package com.budgetx.ai.rules;

import java.math.BigDecimal;
import java.util.List;

import com.budgetx.ai.dto.ActionItem;
import com.budgetx.ai.dto.Insight;

/**
 * What the hard rules decided before any AI is involved.
 *
 * @param emergencyFundContribution part of the monthly savings reserved for the emergency fund
 * @param amountToInvest            what's left to split across {@link InvestmentType}s (0 = don't invest yet)
 * @param equityCapPercent          the most that may go to equity, from risk profile and age
 */
public record RuleOutcome(List<Insight> warnings, List<ActionItem> actions, BigDecimal emergencyFundContribution,
                          BigDecimal amountToInvest, int equityCapPercent, BigDecimal emergencyFundTarget) {

    public boolean canInvest() {
        return amountToInvest.compareTo(FinancialRuleEngine.MIN_MONTHLY_INVESTMENT) >= 0;
    }
}
