package com.budgetx.ai.agent;

import com.budgetx.ai.rules.InvestmentType;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public record AiAllocation(
        @JsonPropertyDescription("One of the allowed categories") InvestmentType type,
        @JsonPropertyDescription("Whole-number percentage of the monthly amount, 1 to 100") int percent,
        @JsonPropertyDescription("One or two short sentences on why this suits this user, tied to their numbers. "
                + "No product, fund, bank or company names and no return figures.") String reason) {
}
