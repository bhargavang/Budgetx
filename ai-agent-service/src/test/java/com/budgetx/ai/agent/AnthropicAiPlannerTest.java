package com.budgetx.ai.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.budgetx.ai.feign.RiskProfile;

class AnthropicAiPlannerTest {

    private final PlanContext context = new PlanContext(29, RiskProfile.MODERATE, new BigDecimal("85000"),
            new BigDecimal("12000"), new BigDecimal("40000"), new BigDecimal("15000"), 50, 15, 1, false, true, false);

    @Test
    void buildsAStructuredRequestWithoutCallingTheApi() {
        var planner = new AnthropicAiPlanner(AnthropicOkHttpClient.builder().apiKey("test-key").build(), "claude-opus-5-5");

        var params = planner.buildParams(context);

        assertThat(params.outputType()).isEqualTo(AiPlan.class);
        assertThat(params.rawParams().model().asString()).isEqualTo("claude-opus-5-5");
        String body = params.rawParams()._body().toString();
        assertThat(body).contains("INDEX_FUND_SIP").contains("PPF");
    }

    @Test
    void userMessageCarriesOnlyAnonymousNumbers() {
        String message = AnthropicAiPlanner.userMessage(context);

        assertThat(message).contains("Age: 29").contains("Amount to split each month: Rs 15000")
                .contains("Equity cap (INDEX_FUND_SIP): 50%").contains("Gold cap: 15%");
        assertThat(message).doesNotContainIgnoringCase("email").doesNotContainIgnoringCase("name");
    }

    @Test
    void systemPromptStatesTheSafetyRules() {
        assertThat(AnthropicAiPlanner.SYSTEM_PROMPT)
                .contains("not a SEBI-registered investment adviser")
                .contains("Never name a specific fund")
                .contains("Never suggest crypto")
                .contains("Never promise, estimate or quote returns");
    }
}
