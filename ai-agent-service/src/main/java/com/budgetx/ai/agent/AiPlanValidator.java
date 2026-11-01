package com.budgetx.ai.agent;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.budgetx.ai.rules.AssetClass;
import com.budgetx.ai.rules.DefaultAllocator;
import com.budgetx.ai.rules.FinancialRuleEngine;
import com.budgetx.ai.rules.InvestmentType;
import com.budgetx.ai.rules.Share;

/**
 * Checks an AI plan against BudgetX's safety rules. Text that mentions a banned instrument, a brand, a link or a
 * return promise is removed. The plan is used only if what remains still adds up to 100% and stays within the
 * equity and gold caps; otherwise the caller falls back to the rules-only split.
 */
@Component
public class AiPlanValidator {

    static final int MAX_REASON_LENGTH = 300;
    static final int MAX_SUMMARY_LENGTH = 600;

    /** Instruments and sales language we never show. */
    private static final Pattern BANNED = Pattern.compile(String.join("|",
            "crypto", "bitcoin", "ethereum", "\\bnft", "\\bf\\s*&\\s*o\\b", "futures", "derivative",
            "(call|put)\\s+options?", "options?\\s+(trading|contracts?)", "intraday", "day[- ]trad", "penny",
            "leverag", "margin\\s+trad", "forex", "binary", "chit", "\\bulip", "endowment", "money[- ]back",
            "guarantee", "assured", "risk[- ]free", "double your", "multibagger", "telegram", "whatsapp",
            "insider", "tips?\\b.*\\bstock", "smallcase"),
            Pattern.CASE_INSENSITIVE);

    /** Links, so nothing can carry an affiliate or referral URL. */
    private static final Pattern LINK = Pattern.compile("https?://|www\\.|\\b[a-z0-9-]+\\.(com|in|co|io|app|net|org)\\b",
            Pattern.CASE_INSENSITIVE);

    /** Specific providers. We describe categories only, never who to buy from. */
    private static final Pattern BRAND = Pattern.compile("\\b(" + String.join("|",
            "sbi", "hdfc", "icici", "axis", "kotak", "nippon", "mirae", "parag\\s+parikh", "ppfas", "quant",
            "tata", "uti", "aditya\\s+birla", "dsp", "motilal", "edelweiss", "franklin", "bandhan", "canara",
            "invesco", "hsbc", "idfc", "yes\\s+bank", "indusind", "lic", "bajaj", "max\\s+life", "star\\s+health",
            "niva", "care\\s+health", "policybazaar", "zerodha", "groww", "upstox", "paytm", "navi", "kuvera",
            "coin", "angel", "5paisa", "niftybees", "goldbees", "juniorbees") + ")\\b", Pattern.CASE_INSENSITIVE);

    /** Return or rate claims such as "12% returns", "8% p.a." or "returns of 10". */
    private static final Pattern RETURN_CLAIM = Pattern.compile(
            "\\d+(\\.\\d+)?\\s*%\\s*(a\\s+year|per\\s+(year|annum)|p\\.?\\s?a|annual|yearly|returns?|cagr|interest|yield)"
                    + "|(returns?|cagr|yield|interest)\\s+(of|around|about|near|upto|up\\s+to)\\s+\\d",
            Pattern.CASE_INSENSITIVE);

    public record Result(List<Share> shares, String summary, List<String> violations) {

        public boolean usable() {
            return shares != null;
        }
    }

    /** Returns a reason the text can't be shown, or null when it is fine. */
    static String problemWith(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        if (BANNED.matcher(text).find()) {
            return "mentions a banned instrument or sales language";
        }
        if (LINK.matcher(text).find()) {
            return "contains a link";
        }
        if (BRAND.matcher(text).find()) {
            return "names a specific provider";
        }
        if (RETURN_CLAIM.matcher(text).find()) {
            return "quotes a return or rate";
        }
        return null;
    }

    public Result validate(AiPlan plan, int equityCap) {
        List<String> violations = new ArrayList<>();
        if (plan == null || plan.allocations() == null || plan.allocations().isEmpty()) {
            violations.add("no allocations");
            return new Result(null, null, violations);
        }

        Map<InvestmentType, Integer> percents = new EnumMap<>(InvestmentType.class);
        Map<InvestmentType, String> reasons = new EnumMap<>(InvestmentType.class);
        for (AiAllocation a : plan.allocations()) {
            if (a == null || a.type() == null) {
                violations.add("unknown category");
                continue;
            }
            if (a.percent() < 0 || a.percent() > 100) {
                violations.add(a.type() + ": percentage out of range");
                return new Result(null, null, violations);
            }
            if (a.percent() == 0) {
                continue;
            }
            String problem = problemWith(a.reason());
            if (problem != null) {
                violations.add(a.type() + ": reason " + problem);
                continue; // drop the whole slice, not just its text
            }
            percents.merge(a.type(), a.percent(), Integer::sum);
            String reason = a.reason() == null || a.reason().isBlank()
                    ? DefaultAllocator.defaultReason(a.type()) : truncate(a.reason().strip(), MAX_REASON_LENGTH);
            reasons.putIfAbsent(a.type(), reason);
        }

        int total = percents.values().stream().mapToInt(Integer::intValue).sum();
        int equity = sum(percents, AssetClass.EQUITY);
        int gold = sum(percents, AssetClass.GOLD);
        if (total < 98 || total > 102) {
            violations.add("percentages add up to " + total + ", not 100");
        }
        if (equity > equityCap) {
            violations.add("equity " + equity + "% is over the " + equityCap + "% cap");
        }
        if (gold > FinancialRuleEngine.MAX_GOLD_PERCENT) {
            violations.add("gold " + gold + "% is over the " + FinancialRuleEngine.MAX_GOLD_PERCENT + "% cap");
        }
        boolean structural = total < 98 || total > 102 || equity > equityCap || gold > FinancialRuleEngine.MAX_GOLD_PERCENT;
        if (structural) {
            return new Result(null, null, violations);
        }

        // Absorb a 1-2 point rounding difference in the largest non-equity slice, so equity never grows.
        if (total != 100) {
            InvestmentType adjust = percents.keySet().stream()
                    .filter(t -> t.assetClass() == AssetClass.DEBT)
                    .max((x, y) -> Integer.compare(percents.get(x), percents.get(y)))
                    .orElse(null);
            if (adjust == null || percents.get(adjust) + (100 - total) <= 0) {
                violations.add("percentages add up to " + total + ", not 100");
                return new Result(null, null, violations);
            }
            percents.merge(adjust, 100 - total, Integer::sum);
        }

        List<Share> shares = new ArrayList<>();
        percents.forEach((type, pct) -> shares.add(new Share(type, pct, reasons.get(type))));

        String summary = plan.summary() == null ? null : plan.summary().strip();
        String summaryProblem = problemWith(summary);
        if (summaryProblem != null) {
            violations.add("summary " + summaryProblem);
            summary = null;
        }
        return new Result(shares, summary == null || summary.isEmpty() ? null : truncate(summary, MAX_SUMMARY_LENGTH),
                violations);
    }

    private static int sum(Map<InvestmentType, Integer> percents, AssetClass assetClass) {
        return percents.entrySet().stream().filter(e -> e.getKey().assetClass() == assetClass)
                .mapToInt(Map.Entry::getValue).sum();
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1).strip() + "…";
    }
}
