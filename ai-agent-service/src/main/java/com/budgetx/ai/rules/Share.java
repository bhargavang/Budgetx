package com.budgetx.ai.rules;

/** A proposed percentage of the amount to invest, with the reason shown to the user. */
public record Share(InvestmentType type, int percent, String reason) {
}
