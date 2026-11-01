package com.budgetx.ai.rules;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.budgetx.ai.dto.ActionItem;
import com.budgetx.ai.dto.Insight;
import com.budgetx.ai.entity.SafetyNet;
import com.budgetx.ai.feign.BudgetSummary;
import com.budgetx.ai.feign.RiskProfile;
import com.budgetx.ai.feign.UserProfile;

/**
 * Hard financial-safety rules. They run before the AI and the AI can't override them: spending more than you
 * earn, heavy EMIs, no emergency fund and missing insurance all come before any investing.
 */
@Component
public class FinancialRuleEngine {

    /** Below this a monthly SIP isn't practical, so we suggest saving instead. */
    public static final BigDecimal MIN_MONTHLY_INVESTMENT = new BigDecimal("500");
    public static final int MAX_GOLD_PERCENT = 15;
    static final int EMERGENCY_FUND_MONTHS = 6;

    public static int equityCap(RiskProfile risk, Integer age) {
        int cap = switch (risk == null ? RiskProfile.MODERATE : risk) {
            case CONSERVATIVE -> 30;
            case MODERATE -> 50;
            case AGGRESSIVE -> 70;
        };
        if (age != null && age > 0) {
            cap = Math.min(cap, Math.max(0, 100 - age)); // older users get less equity, whatever they picked
        }
        return cap;
    }

    public RuleOutcome apply(UserProfile profile, BudgetSummary budget, SafetyNet safety) {
        List<Insight> warnings = new ArrayList<>();
        List<ActionItem> actions = new ArrayList<>();
        BigDecimal salary = orZero(budget.monthlySalary());
        BigDecimal emi = orZero(budget.totalEmi());
        BigDecimal spending = orZero(budget.spendingEstimate());
        BigDecimal investable = orZero(budget.investableAmount()).max(BigDecimal.ZERO);
        int cap = equityCap(profile.riskProfile(), profile.age());

        // Monthly essentials drive the emergency-fund target. With no spending logged yet, assume half the salary.
        BigDecimal monthlyOutgo = emi.add(spending);
        if (monthlyOutgo.signum() == 0) {
            monthlyOutgo = salary.divide(BigDecimal.valueOf(2), 0, RoundingMode.HALF_UP);
        }
        BigDecimal target = monthlyOutgo.multiply(BigDecimal.valueOf(EMERGENCY_FUND_MONTHS));

        if (salary.signum() == 0) {
            warnings.add(new Insight("CRITICAL", "Add your monthly salary",
                    "We need your take-home salary to work out what you can safely save. Add it in Profile."));
            return new RuleOutcome(warnings, actions, BigDecimal.ZERO, BigDecimal.ZERO, cap, target);
        }

        // Rule: spending more than you earn
        if (budget.overspent() || orZero(budget.disposableIncome()).signum() <= 0) {
            warnings.add(new Insight("CRITICAL", "You're spending more than you earn",
                    "Your EMIs and spending (" + inr(emi.add(spending)) + ") are at or above your salary ("
                            + inr(salary) + "). Don't start investing yet. First cut back on wants such as eating out "
                            + "and shopping, and avoid new loans or credit card debt."));
            investable = BigDecimal.ZERO;
        }

        // Rule: EMI burden
        BigDecimal emiRatio = orZero(budget.emiToIncomePercent());
        if (emiRatio.compareTo(BigDecimal.valueOf(50)) > 0) {
            warnings.add(new Insight("CRITICAL", "Your EMIs take " + emiRatio.stripTrailingZeros().toPlainString()
                    + "% of your salary",
                    "Above 50% leaves very little room for emergencies. Avoid taking any new loan, and consider using "
                            + "spare money to prepay the loan with the highest interest rate first (credit cards and "
                            + "personal loans usually cost far more than any investment earns)."));
            actions.add(new ActionItem("DEBT", "Bring your EMIs down",
                    "Prepay your costliest loan before investing more. Paying off a 14% loan is a sure 14% saving.",
                    null));
        } else if (emiRatio.compareTo(BigDecimal.valueOf(30)) > 0) {
            warnings.add(new Insight("WARNING", "Your EMIs take " + emiRatio.stripTrailingZeros().toPlainString()
                    + "% of your salary",
                    "A common guideline is to keep all EMIs under 30 to 40% of take-home pay. Think twice before "
                            + "taking another loan."));
        }

        boolean missing = safety == null;
        BigDecimal savings = missing ? BigDecimal.ZERO : orZero(safety.getEmergencySavings());

        // Rule 1: emergency fund before investing
        BigDecimal emergency = BigDecimal.ZERO;
        BigDecimal gap = target.subtract(savings);
        if (gap.signum() > 0) {
            BigDecimal monthsCovered = savings.divide(monthlyOutgo, 1, RoundingMode.DOWN);
            // Under 3 months covered: most of the monthly savings go here. 3 to 6 months: a smaller share.
            BigDecimal share = monthsCovered.compareTo(BigDecimal.valueOf(3)) < 0 ? new BigDecimal("0.70") : new BigDecimal("0.40");
            emergency = investable.multiply(share).setScale(0, RoundingMode.DOWN).min(gap);
            actions.add(new ActionItem("EMERGENCY_FUND", "Build an emergency fund first",
                    "Aim for " + EMERGENCY_FUND_MONTHS + " months of expenses and EMIs, about " + inr(target)
                            + (missing ? "." : ". You have " + inr(savings) + " (about " + monthsCovered.toPlainString()
                            + " months).")
                            + " Keep it in a savings account, sweep-in FD or liquid fund that you can withdraw in a day, "
                            + "not in shares. It stops a job loss or hospital bill from turning into debt.",
                    emergency.signum() > 0 ? emergency : null));
        }

        // Rule 3 (health first: one hospital stay can wipe out years of savings)
        if (missing || !safety.isHasHealthInsurance()) {
            actions.add(new ActionItem("HEALTH_INSURANCE", "Get your own health insurance",
                    "Look for at least ₹5 lakh family floater cover (₹10 lakh in a metro). Cover from your employer "
                            + "usually ends when you leave the job, so have a personal policy too. Compare claim "
                            + "settlement ratios and waiting periods on the IRDAI-regulated insurer's own documents. "
                            + "Pay the premium from the money left after expenses, before investing.",
                    null));
        }

        // Rule 2: term insurance when someone depends on your income
        int dependents = missing ? 0 : safety.getDependents();
        Integer age = profile.age();
        if (!missing && !safety.isHasTermInsurance()) {
            if (dependents > 0 && (age == null || age < 55)) {
                BigDecimal cover = salary.multiply(BigDecimal.valueOf(12 * 10));
                actions.add(new ActionItem("TERM_INSURANCE", "Get a pure term life plan",
                        "Since " + dependents + (dependents == 1 ? " person depends" : " people depend")
                                + " on your income, a term plan of about 10 to 15 times your yearly income (around "
                                + inr(cover) + " or more) protects them if something happens to you. Buy pure term "
                                + "only. Avoid endowment, money-back and ULIP policies that mix insurance with "
                                + "investment: they cost far more for much less cover.",
                        null));
            } else if (dependents == 0) {
                warnings.add(new Insight("INFO", "Term insurance can wait",
                        "Nobody depends on your income right now, so life cover isn't urgent. Revisit this when you "
                                + "marry, have children or support your parents."));
            }
        }
        if (missing) {
            warnings.add(new Insight("INFO", "Tell us about your safety net",
                    "Add your emergency savings, dependents and insurance below so the guidance fits you. Until then "
                            + "we assume you have no emergency fund and no insurance."));
        }

        BigDecimal toInvest = investable.subtract(emergency).max(BigDecimal.ZERO);
        if (toInvest.signum() > 0 && toInvest.compareTo(MIN_MONTHLY_INVESTMENT) < 0) {
            warnings.add(new Insight("INFO", "Start small",
                    "After the steps above about " + inr(toInvest) + " is left each month. Many index funds accept SIPs "
                            + "from ₹500 a month, so start once you can set aside that much."));
        }
        return new RuleOutcome(warnings, actions, emergency, toInvest, cap, target);
    }

    /** Rupees with Indian digit grouping, e.g. ₹12,34,567. */
    static String inr(BigDecimal amount) {
        String digits = amount.setScale(0, RoundingMode.HALF_UP).abs().toPlainString();
        StringBuilder out = new StringBuilder();
        int n = digits.length();
        for (int i = 0; i < n; i++) {
            int fromEnd = n - i;
            out.append(digits.charAt(i));
            if (fromEnd > 1 && (fromEnd == 4 || (fromEnd > 4 && (fromEnd - 4) % 2 == 0))) {
                out.append(',');
            }
        }
        return (amount.signum() < 0 ? "-₹" : "₹") + out;
    }

    private static BigDecimal orZero(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
