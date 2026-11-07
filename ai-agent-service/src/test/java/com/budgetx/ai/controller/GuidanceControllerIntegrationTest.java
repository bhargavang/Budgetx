package com.budgetx.ai.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.budgetx.ai.agent.AiAllocation;
import com.budgetx.ai.agent.AiPlan;
import com.budgetx.ai.agent.AiPlanner;
import com.budgetx.ai.agent.AiUnavailableException;
import com.budgetx.ai.feign.BudgetClient;
import com.budgetx.ai.feign.BudgetSummary;
import com.budgetx.ai.feign.RiskProfile;
import com.budgetx.ai.feign.UserClient;
import com.budgetx.ai.feign.UserProfile;
import com.budgetx.ai.repository.GuidanceRepository;
import com.budgetx.ai.repository.SafetyNetRepository;
import com.budgetx.ai.rules.InvestmentType;

@SpringBootTest(properties = "budgetx.ai.daily-limit-per-user=2")
@AutoConfigureMockMvc
class GuidanceControllerIntegrationTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private GuidanceRepository guidance;
    @Autowired
    private SafetyNetRepository safetyNets;

    @MockBean
    private UserClient userClient;
    @MockBean
    private BudgetClient budgetClient;
    @MockBean
    private AiPlanner aiPlanner;

    private static final String SAFE_NET = """
            {"emergencySavings": 500000, "dependents": 2, "hasTermInsurance": true, "hasHealthInsurance": true}""";

    @BeforeEach
    void setUp() {
        guidance.deleteAll();
        safetyNets.deleteAll();
        when(userClient.getProfile(1L)).thenReturn(new UserProfile(1L, 30, new BigDecimal("100000"), RiskProfile.MODERATE));
        when(budgetClient.getSummary(1L)).thenReturn(new BudgetSummary(10, 2026, new BigDecimal("100000"),
                new BigDecimal("10000"), new BigDecimal("40000"), new BigDecimal("50000"), new BigDecimal("30000"),
                false, new BigDecimal("10.0")));
    }

    private void saveSafetyNet() throws Exception {
        mvc.perform(put("/api/ai/safety-profile").header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON).content(SAFE_NET))
                .andExpect(status().isOk());
    }

    @Test
    void rulesOnlyWhenNoApiKey() throws Exception {
        when(aiPlanner.isEnabled()).thenReturn(false);
        saveSafetyNet();

        mvc.perform(post("/api/recommendations/generate").header("X-User-Id", "1"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mode").value("RULES_ONLY"))
                .andExpect(jsonPath("$.equityCapPercent").value(50))
                .andExpect(jsonPath("$.budget.amountToInvest").value(30000))
                .andExpect(jsonPath("$.allocations[0].type").value("INDEX_FUND_SIP"))
                .andExpect(jsonPath("$.allocations[0].monthlyAmount").value(15000))
                .andExpect(jsonPath("$.disclaimer", containsString("not a SEBI-registered")))
                .andExpect(jsonPath("$.principles", hasItem(containsString("no commissions"))));
        verify(aiPlanner, never()).propose(any());
    }

    @Test
    void aiPlanThatPassesTheRulesIsUsed() throws Exception {
        when(aiPlanner.isEnabled()).thenReturn(true);
        when(aiPlanner.propose(any())).thenReturn(new AiPlan(List.of(
                new AiAllocation(InvestmentType.INDEX_FUND_SIP, 40, "At 30 you have time for growth."),
                new AiAllocation(InvestmentType.DEBT_FUND, 25, "For a goal a few years away."),
                new AiAllocation(InvestmentType.PPF, 25, "A safe long-term base."),
                new AiAllocation(InvestmentType.GOLD, 10, "Balances market swings.")),
                "Grow steadily while keeping most of your money safe."));
        saveSafetyNet();

        mvc.perform(post("/api/recommendations/generate").header("X-User-Id", "1"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mode").value("AI_CHECKED"))
                .andExpect(jsonPath("$.summary").value("Grow steadily while keeping most of your money safe."))
                .andExpect(jsonPath("$.allocations[0].percent").value(40))
                .andExpect(jsonPath("$.allocations[0].title").value("Index fund SIP"))
                .andExpect(jsonPath("$.allocations[0].reason").value("At 30 you have time for growth."));

        mvc.perform(get("/api/recommendations/latest").header("X-User-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("AI_CHECKED"));
    }

    @Test
    void aiPlanThatBreaksTheRulesFallsBackToRules() throws Exception {
        when(aiPlanner.isEnabled()).thenReturn(true);
        when(aiPlanner.propose(any())).thenReturn(new AiPlan(List.of(
                new AiAllocation(InvestmentType.INDEX_FUND_SIP, 80, "Go big on equity."),
                new AiAllocation(InvestmentType.PPF, 20, "Some safety.")), "Mostly shares."));
        saveSafetyNet();

        mvc.perform(post("/api/recommendations/generate").header("X-User-Id", "1"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mode").value("RULES_ONLY"))
                .andExpect(jsonPath("$.modeNote", containsString("didn't pass our safety checks")))
                .andExpect(jsonPath("$.summary").doesNotExist())
                .andExpect(jsonPath("$.allocations[0].percent").value(50));
    }

    @Test
    void aiOutageFallsBackToRules() throws Exception {
        when(aiPlanner.isEnabled()).thenReturn(true);
        when(aiPlanner.propose(any())).thenThrow(new AiUnavailableException("timeout"));

        mvc.perform(post("/api/recommendations/generate").header("X-User-Id", "1"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mode").value("RULES_ONLY"))
                .andExpect(jsonPath("$.safetyProfileMissing").value(true))
                .andExpect(jsonPath("$.actions[*].type", hasItem("EMERGENCY_FUND")))
                .andExpect(jsonPath("$.allocations", not(empty())));
    }

    @Test
    void dailyAiLimitKeepsCostsBounded() throws Exception {
        when(aiPlanner.isEnabled()).thenReturn(true);
        when(aiPlanner.propose(any())).thenThrow(new AiUnavailableException("timeout"));
        saveSafetyNet();

        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/api/recommendations/generate").header("X-User-Id", "1")).andExpect(status().isCreated());
        }

        verify(aiPlanner, times(2)).propose(any());
        mvc.perform(get("/api/ai/status").header("X-User-Id", "1"))
                .andExpect(jsonPath("$.aiEnabled").value(true))
                .andExpect(jsonPath("$.aiRemainingToday").value(0));
    }

    @Test
    void overspendingSkipsInvestingAndTheAi() throws Exception {
        when(aiPlanner.isEnabled()).thenReturn(true);
        when(budgetClient.getSummary(1L)).thenReturn(new BudgetSummary(10, 2026, new BigDecimal("40000"),
                new BigDecimal("25000"), new BigDecimal("20000"), new BigDecimal("-5000"), BigDecimal.ZERO,
                true, new BigDecimal("62.5")));

        mvc.perform(post("/api/recommendations/generate").header("X-User-Id", "1"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.allocations", empty()))
                .andExpect(jsonPath("$.warnings[0].severity").value("CRITICAL"));
        verify(aiPlanner, never()).propose(any());
    }

    @Test
    void safetyProfileRoundTripAndValidation() throws Exception {
        mvc.perform(get("/api/ai/safety-profile").header("X-User-Id", "1"))
                .andExpect(jsonPath("$.saved").value(false));
        saveSafetyNet();
        mvc.perform(get("/api/ai/safety-profile").header("X-User-Id", "1"))
                .andExpect(jsonPath("$.saved").value(true))
                .andExpect(jsonPath("$.dependents").value(2));
        mvc.perform(put("/api/ai/safety-profile").header("X-User-Id", "1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"emergencySavings\": -1, \"dependents\": 1, \"hasTermInsurance\": false, \"hasHealthInsurance\": false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.emergencySavings").exists());
    }

    @Test
    void latestIs404BeforeAnyGuidanceAndHeaderIsRequired() throws Exception {
        mvc.perform(get("/api/recommendations/latest").header("X-User-Id", "1")).andExpect(status().isNotFound());
        mvc.perform(post("/api/recommendations/generate")).andExpect(status().isUnauthorized());
    }
}
