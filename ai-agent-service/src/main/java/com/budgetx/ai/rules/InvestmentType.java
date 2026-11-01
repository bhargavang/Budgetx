package com.budgetx.ai.rules;

/**
 * The only categories BudgetX ever suggests. All are regulated, widely available and generic: we never name a
 * specific fund, scheme, bank or company, and the titles and descriptions shown to users come from here, not
 * from the AI.
 */
public enum InvestmentType {

    INDEX_FUND_SIP(AssetClass.EQUITY, "HIGH", "5+ years",
            "Index fund SIP",
            "A fixed monthly amount into a low-cost mutual fund that tracks a broad index such as the Nifty 50. "
                    + "Choose a direct plan with a low expense ratio. Its value can fall sharply in the short term, "
                    + "so only use money you won't need for at least 5 years."),
    DEBT_FUND(AssetClass.DEBT, "LOW_TO_MEDIUM", "1-3 years",
            "Short-duration debt or liquid fund",
            "Mutual funds that lend to the government and companies. Steadier than equity but not risk-free, "
                    + "and returns are not fixed. Useful for goals 1 to 3 years away."),
    PPF(AssetClass.DEBT, "LOW", "15 years",
            "Public Provident Fund (PPF)",
            "A government-backed savings scheme available at post offices and banks. The interest rate is set by "
                    + "the government every quarter. Money is locked for 15 years (partial withdrawals from year 7), "
                    + "up to ₹1.5 lakh a year."),
    FIXED_DEPOSIT(AssetClass.DEBT, "LOW", "Up to 5 years",
            "Bank fixed or recurring deposit",
            "A deposit at a bank for a fixed term at a fixed rate. Deposits up to ₹5 lakh per bank are insured "
                    + "by DICGC. Interest is taxed at your income-tax slab."),
    GOLD(AssetClass.GOLD, "MEDIUM", "5+ years",
            "Gold ETF or gold fund",
            "Gold held through an exchange-traded fund or a gold mutual fund instead of jewellery, so there are no "
                    + "making charges. Gold prices swing, so keep it a small part of your savings.");

    private final AssetClass assetClass;
    private final String riskLevel;
    private final String horizon;
    private final String title;
    private final String about;

    InvestmentType(AssetClass assetClass, String riskLevel, String horizon, String title, String about) {
        this.assetClass = assetClass;
        this.riskLevel = riskLevel;
        this.horizon = horizon;
        this.title = title;
        this.about = about;
    }

    public AssetClass assetClass() {
        return assetClass;
    }

    public String riskLevel() {
        return riskLevel;
    }

    public String horizon() {
        return horizon;
    }

    public String title() {
        return title;
    }

    public String about() {
        return about;
    }
}
