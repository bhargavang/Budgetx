package com.budgetx.ai.agent;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.anthropic.models.messages.StructuredTextBlock;

import lombok.extern.slf4j.Slf4j;

/**
 * Asks Claude for an allocation using structured outputs, so the reply is JSON matching {@link AiPlan}.
 * Whatever comes back is still treated as untrusted and checked by {@link AiPlanValidator}.
 */
@Slf4j
public class AnthropicAiPlanner implements AiPlanner {

    static final String SYSTEM_PROMPT = """
            You help users of BudgetX, a free personal-finance app in India, understand how they could split a \
            monthly savings amount across a few broad, regulated categories. This is free general education, not \
            personalised investment advice, and BudgetX is not a SEBI-registered investment adviser. BudgetX earns \
            nothing from any product, so never favour or promote one.

            BudgetX's rule engine has already handled the emergency fund, insurance and debt. You only split the \
            amount you are given. Rules you must follow:
            - Use only the categories in the schema. Never name a specific fund, scheme, AMC, bank, insurer, broker, \
            app, stock, index ETF ticker or website.
            - Never suggest crypto, F&O or other derivatives, intraday trading, penny stocks, leverage, unregulated \
            schemes, chit funds, or ULIP, endowment or money-back policies.
            - INDEX_FUND_SIP is the only equity category; its percentage must not exceed the equity cap you are given. \
            GOLD must not exceed the gold cap. Percentages are whole numbers that add up to exactly 100.
            - Equity is always a monthly SIP, never a lump sum.
            - Never promise, estimate or quote returns or interest rates. Never say guaranteed, assured or risk-free.
            - Explain each choice in one or two short sentences a first-time investor understands, linked to the \
            user's age, risk profile, dependents or numbers.
            """;

    private final AnthropicClient client;
    private final String model;

    public AnthropicAiPlanner(AnthropicClient client, String model) {
        this.client = client;
        this.model = model;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    StructuredMessageCreateParams<AiPlan> buildParams(PlanContext c) {
        return MessageCreateParams.builder()
                .model(model)
                .maxTokens(8000L)
                .system(SYSTEM_PROMPT)
                .outputConfig(AiPlan.class)
                .addUserMessage(userMessage(c))
                .build();
    }

    static String userMessage(PlanContext c) {
        return """
                Suggest how to split this user's monthly amount.

                Age: %s
                Risk profile chosen by the user: %s
                Monthly take-home salary: Rs %s
                Monthly EMIs: Rs %s
                Monthly spending (estimate): Rs %s
                People who depend on their income: %d
                Emergency fund already complete: %s
                Has health insurance: %s
                Has term life insurance: %s

                Amount to split each month: Rs %s
                Equity cap (INDEX_FUND_SIP): %d%%
                Gold cap: %d%%
                """.formatted(c.age() == null ? "not given" : c.age(), c.riskProfile(), c.monthlySalary().toPlainString(),
                c.monthlyEmi().toPlainString(), c.monthlySpending().toPlainString(), c.dependents(),
                yesNo(c.emergencyFundComplete()), yesNo(c.hasHealthInsurance()), yesNo(c.hasTermInsurance()),
                c.amountToInvest().toPlainString(), c.equityCapPercent(), c.maxGoldPercent());
    }

    private static String yesNo(boolean b) {
        return b ? "yes" : "no";
    }

    @Override
    public AiPlan propose(PlanContext context) {
        StructuredMessage<AiPlan> response;
        try {
            response = client.messages().create(buildParams(context));
        } catch (RuntimeException e) {
            throw new AiUnavailableException("Claude request failed: " + e.getClass().getSimpleName(), e);
        }
        StopReason stop = response.stopReason().orElse(null);
        if (StopReason.REFUSAL.equals(stop) || StopReason.MAX_TOKENS.equals(stop)) {
            throw new AiUnavailableException("Claude stopped early: " + stop);
        }
        try {
            return response.content().stream()
                    .flatMap(block -> block.text().stream())
                    .map(StructuredTextBlock::text)
                    .findFirst()
                    .orElseThrow(() -> new AiUnavailableException("Claude returned no plan"));
        } catch (AiUnavailableException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new AiUnavailableException("Claude's reply didn't match the expected format", e);
        }
    }
}
