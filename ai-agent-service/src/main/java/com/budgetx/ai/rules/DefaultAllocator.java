package com.budgetx.ai.rules;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.budgetx.ai.feign.RiskProfile;

/** The rules-only split, used when there is no AI key or the AI's answer fails validation. */
@Component
public class DefaultAllocator {

    static final int GOLD_PERCENT = 10;

    public List<Share> allocate(RiskProfile risk, int equityCap) {
        int equity = equityCap;
        int gold = GOLD_PERCENT;
        int debt = 100 - equity - gold;
        List<Share> shares = new ArrayList<>();
        if (equity > 0) {
            shares.add(new Share(InvestmentType.INDEX_FUND_SIP, equity,
                    "Equity is the part that can grow faster than inflation over many years. " + equity
                            + "% is the most we suggest for your risk profile and age, through a monthly SIP so you "
                            + "buy at different prices instead of timing the market."));
        }
        // Long-term safe money in PPF; the rest in a bank deposit that's easier to reach.
        int ppf = debt / 2;
        int fd = debt - ppf;
        if (ppf > 0) {
            shares.add(new Share(InvestmentType.PPF, ppf,
                    "A government-backed, tax-efficient base for long-term goals that doesn't swing with the market."));
        }
        if (fd > 0) {
            shares.add(new Share(InvestmentType.FIXED_DEPOSIT, fd,
                    "Safe money you can reach within a few years for goals like a vehicle or a course."));
        }
        shares.add(new Share(InvestmentType.GOLD, gold,
                "A small amount of gold tends to hold value when shares fall, which smooths out the ups and downs."));
        return shares;
    }

    /** Default reason for a type when the AI gave none or its reason was removed. */
    public static String defaultReason(InvestmentType type) {
        return switch (type) {
            case INDEX_FUND_SIP -> "For long-term growth through a monthly SIP.";
            case DEBT_FUND -> "For steadier returns on money needed in 1 to 3 years.";
            case PPF -> "For a safe, government-backed long-term base.";
            case FIXED_DEPOSIT -> "For safe money you may need within a few years.";
            case GOLD -> "To balance out swings in the share market.";
        };
    }

    static RiskProfile orDefault(RiskProfile risk) {
        return risk == null ? RiskProfile.MODERATE : risk;
    }
}
