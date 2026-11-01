package com.budgetx.budget.controller;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;

import com.budgetx.budget.feign.EmiClient;
import com.budgetx.budget.feign.ExpenseClient;
import com.budgetx.budget.feign.RiskProfile;
import com.budgetx.budget.feign.UserClient;
import com.budgetx.budget.feign.UserProfile;

import feign.FeignException;
import feign.Request;

@SpringBootTest
@AutoConfigureMockMvc
class BudgetControllerIntegrationTest {

    @TestConfiguration
    static class FixedClock {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-10-15T10:00:00Z"), ZoneOffset.UTC);
        }
    }

    @Autowired
    private MockMvc mvc;

    @MockBean
    private UserClient userClient;
    @MockBean
    private EmiClient emiClient;
    @MockBean
    private ExpenseClient expenseClient;

    @Test
    void currentBudgetForSignedInUser() throws Exception {
        when(userClient.getProfile(7L)).thenReturn(new UserProfile(7L, new BigDecimal("80000"), RiskProfile.AGGRESSIVE));
        when(emiClient.getTotalEmi(7L, 10, 2026)).thenReturn(new BigDecimal("10000"));
        when(expenseClient.getTotalExpenses(7L, 10, 2026)).thenReturn(new BigDecimal("20000"));
        when(expenseClient.getTotalExpenses(7L, 9, 2026)).thenReturn(new BigDecimal("30000"));

        mvc.perform(get("/api/budgets/current").header("X-User-Id", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value(10))
                .andExpect(jsonPath("$.spendingEstimate").value(30000))
                .andExpect(jsonPath("$.disposableIncome").value(40000))
                .andExpect(jsonPath("$.investableAmount").value(28000))
                .andExpect(jsonPath("$.overspent").value(false));
    }

    @Test
    void internalSummaryForAGivenMonth() throws Exception {
        when(userClient.getProfile(7L)).thenReturn(new UserProfile(7L, new BigDecimal("50000"), RiskProfile.MODERATE));
        when(emiClient.getTotalEmi(7L, 1, 2026)).thenReturn(BigDecimal.ZERO);
        when(expenseClient.getTotalExpenses(7L, 1, 2026)).thenReturn(new BigDecimal("10000"));
        when(expenseClient.getTotalExpenses(7L, 12, 2025)).thenReturn(BigDecimal.ZERO);

        mvc.perform(get("/api/budgets/summary").param("userId", "7").param("month", "1").param("year", "2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.investableAmount").value(24000));
    }

    @Test
    void missingUserHeaderIs401() throws Exception {
        mvc.perform(get("/api/budgets/current")).andExpect(status().isUnauthorized());
    }

    @Test
    void upstreamOutageIs503() throws Exception {
        Request request = Request.create(Request.HttpMethod.GET, "/x", java.util.Map.of(), null, null, null);
        when(userClient.getProfile(anyLong())).thenThrow(new FeignException.ServiceUnavailable("down", request, null, null));

        mvc.perform(get("/api/budgets/current").header("X-User-Id", "7"))
                .andExpect(status().isServiceUnavailable());
    }
}
