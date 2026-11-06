package com.budgetx.ai.agent;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/** The structured answer we ask Claude for. The JSON schema sent to the API is generated from this record. */
public record AiPlan(
        @JsonPropertyDescription("How to split the monthly amount. Percentages must add up to exactly 100.")
        List<AiAllocation> allocations,
        @JsonPropertyDescription("Two to four short sentences explaining the overall idea in plain, simple English "
                + "for a first-time investor. No product names, no return figures.")
        String summary) {
}
