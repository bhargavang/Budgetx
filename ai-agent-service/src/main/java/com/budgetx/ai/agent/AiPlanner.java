package com.budgetx.ai.agent;

/** Asks an AI model for a split. Implementations throw {@link AiUnavailableException} on any failure. */
public interface AiPlanner {

    boolean isEnabled();

    AiPlan propose(PlanContext context);
}
