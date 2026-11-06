package com.budgetx.ai.agent;

/** Used when ANTHROPIC_API_KEY is not set: BudgetX works rules-only. */
public class DisabledAiPlanner implements AiPlanner {

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public AiPlan propose(PlanContext context) {
        throw new AiUnavailableException("AI is not configured");
    }
}
