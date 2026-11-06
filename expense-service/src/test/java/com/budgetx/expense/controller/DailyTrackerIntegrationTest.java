package com.budgetx.expense.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.budgetx.expense.dto.ExpenseEvent;
import com.budgetx.expense.feign.EmiClient;
import com.budgetx.expense.feign.UserClient;
import com.budgetx.expense.repository.DailyBudgetSettingsRepository;
import com.budgetx.expense.repository.DayNoteRepository;
import com.budgetx.expense.repository.ExpenseRepository;

@SpringBootTest
@AutoConfigureMockMvc
class DailyTrackerIntegrationTest {

    /** "Today" is 2026-10-15; October has 31 days, so 17 days are left including today. */
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

    @Autowired
    private ExpenseRepository expenseRepository;

    @Autowired
    private DayNoteRepository dayNoteRepository;

    @Autowired
    private DailyBudgetSettingsRepository settingsRepository;

    @MockBean
    private KafkaTemplate<String, ExpenseEvent> kafkaTemplate;

    @MockBean
    private UserClient userClient;

    @MockBean
    private EmiClient emiClient;

    @BeforeEach
    void setUp() {
        expenseRepository.deleteAll();
        dayNoteRepository.deleteAll();
        settingsRepository.deleteAll();
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(new CompletableFuture<>());
        when(userClient.getSalary(1L)).thenReturn(new BigDecimal("62000"));
        when(emiClient.getTotalEmi(1L, 10, 2026)).thenReturn(new BigDecimal("15000"));
    }

    @Test
    void dailyBudgetComesFromSalaryMinusEmiAndSavings() throws Exception {
        mvc.perform(put("/api/expenses/daily/settings").header("X-User-Id", 1)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"monthlySavingsGoal\":16000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monthlySavingsGoal").value(16000));

        expense("9000", "2026-10-01", null);                       // earlier this month
        expense("300", "2026-10-15", "Chai and samosa with the team"); // today, with a note
        expense("500", "2026-09-30", null);                        // last month: ignored

        // spendable = 62000 - 15000 - 16000 = 31000 -> 1000/day over 31 days.
        // Left at the start of today: 31000 - 9000 = 22000 over 17 days = 1294.11.
        mvc.perform(get("/api/expenses/daily").header("X-User-Id", 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value("2026-10-15"))
                .andExpect(jsonPath("$.monthlySalary").value(62000))
                .andExpect(jsonPath("$.monthlyEmi").value(15000))
                .andExpect(jsonPath("$.emiAvailable").value(true))
                .andExpect(jsonPath("$.spendableThisMonth").value(31000))
                .andExpect(jsonPath("$.daysLeftInMonth").value(17))
                .andExpect(jsonPath("$.dailyBudget").value(1000.00))
                .andExpect(jsonPath("$.todaysAllowance").value(1294.11))
                .andExpect(jsonPath("$.spentToday").value(300.00))
                .andExpect(jsonPath("$.remainingToday").value(994.11))
                .andExpect(jsonPath("$.spentThisMonth").value(9300.00))
                .andExpect(jsonPath("$.remainingThisMonth").value(21700.00))
                .andExpect(jsonPath("$.expenses.length()").value(1))
                .andExpect(jsonPath("$.expenses[0].note").value("Chai and samosa with the team"))
                .andExpect(jsonPath("$.dayNote").doesNotExist());
    }

    @Test
    void dayNoteCanBeSavedReadAndCleared() throws Exception {
        mvc.perform(put("/api/expenses/daily/2026-10-14/note").header("X-User-Id", 1)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"  Paid back Ravi for lunch  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.note").value("Paid back Ravi for lunch"));

        mvc.perform(get("/api/expenses/daily").param("date", "2026-10-14").header("X-User-Id", 1))
                .andExpect(jsonPath("$.dayNote").value("Paid back Ravi for lunch"));
        // Notes are per user.
        when(userClient.getSalary(2L)).thenReturn(new BigDecimal("30000"));
        mvc.perform(get("/api/expenses/daily").param("date", "2026-10-14").header("X-User-Id", 2))
                .andExpect(jsonPath("$.dayNote").doesNotExist());

        mvc.perform(put("/api/expenses/daily/2026-10-14/note").header("X-User-Id", 1)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/expenses/daily").param("date", "2026-10-14").header("X-User-Id", 1))
                .andExpect(jsonPath("$.dayNote").doesNotExist());
    }

    @Test
    void overspendingShowsNegativeRemainingAndAllowanceNeverGoesBelowZero() throws Exception {
        expense("50000", "2026-10-02", null);
        expense("200", "2026-10-15", null);

        // 62000 - 15000 = 47000 spendable; 50000 already spent before today.
        mvc.perform(get("/api/expenses/daily").header("X-User-Id", 1))
                .andExpect(jsonPath("$.todaysAllowance").value(0.00))
                .andExpect(jsonPath("$.remainingToday").value(-200.00))
                .andExpect(jsonPath("$.remainingThisMonth").value(-3200.00));
    }

    @Test
    void missingEmiDataStillGivesABudget() throws Exception {
        when(emiClient.getTotalEmi(anyLong(), anyInt(), anyInt())).thenThrow(new RuntimeException("down"));

        mvc.perform(get("/api/expenses/daily").header("X-User-Id", 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emiAvailable").value(false))
                .andExpect(jsonPath("$.monthlyEmi").value(0))
                .andExpect(jsonPath("$.dailyBudget").value(2000.00));
    }

    @Test
    void userServiceDownIs503AndFutureDatesAreRejected() throws Exception {
        mvc.perform(get("/api/expenses/daily").param("date", "2026-10-16").header("X-User-Id", 1))
                .andExpect(status().isBadRequest());

        when(userClient.getSalary(1L)).thenThrow(new RuntimeException("down"));
        mvc.perform(get("/api/expenses/daily").header("X-User-Id", 1))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void negativeSavingsGoalAndOverlongNotesAreRejected() throws Exception {
        mvc.perform(put("/api/expenses/daily/settings").header("X-User-Id", 1)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"monthlySavingsGoal\":-1}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/expenses").header("X-User-Id", 1).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryId\":2,\"amount\":10,\"expenseDate\":\"2026-10-15\",\"note\":\""
                                + "x".repeat(1001) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.note").exists());
    }

    private void expense(String amount, String date, String note) throws Exception {
        String noteJson = note == null ? "null" : "\"" + note + "\"";
        mvc.perform(post("/api/expenses").header("X-User-Id", 1).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryId\":2,\"amount\":" + amount + ",\"expenseDate\":\"" + date
                                + "\",\"note\":" + noteJson + "}"))
                .andExpect(status().isCreated());
    }
}
