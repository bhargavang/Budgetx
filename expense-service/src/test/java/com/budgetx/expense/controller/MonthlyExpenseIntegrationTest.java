package com.budgetx.expense.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.AfterEach;
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
import com.budgetx.expense.repository.ExpenseRepository;
import com.budgetx.expense.repository.MonthlyExpenseRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class MonthlyExpenseIntegrationTest {

    /** "Today" is 2026-10-15; October has 31 days. */
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
    private ObjectMapper json;

    @Autowired
    private MonthlyExpenseRepository monthlyRepository;

    @Autowired
    private ExpenseRepository expenseRepository;

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
        monthlyRepository.deleteAll();
        expenseRepository.deleteAll();
        settingsRepository.deleteAll();
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(new CompletableFuture<>());
        when(userClient.getSalary(1L)).thenReturn(new BigDecimal("62000"));
        when(emiClient.getTotalEmi(1L, 10, 2026)).thenReturn(new BigDecimal("15000"));
    }

    @AfterEach
    void tearDown() {
        monthlyRepository.deleteAll();
        expenseRepository.deleteAll();
    }

    @Test
    void monthlyExpensesCanBeAddedListedEditedPausedAndDeleted() throws Exception {
        long rent = create(1, "{\"name\":\"  Flat rent \",\"amount\":12000,\"type\":\"RENT\",\"dueDay\":5,"
                + "\"note\":\"Pay owner by UPI\"}");
        long netflix = create(1, "{\"name\":\"Netflix\",\"amount\":199,\"type\":\"SUBSCRIPTION\",\"dueDay\":20}");
        create(1, "{\"name\":\"Maid\",\"amount\":2500}");

        mvc.perform(get("/api/expenses/monthly").header("X-User-Id", 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monthlyTotal").value(14699))
                .andExpect(jsonPath("$.activeCount").value(3))
                .andExpect(jsonPath("$.items[0].name").value("Flat rent"))
                .andExpect(jsonPath("$.items[0].note").value("Pay owner by UPI"))
                .andExpect(jsonPath("$.items[1].name").value("Netflix"))
                .andExpect(jsonPath("$.items[2].type").value("OTHER"))
                .andExpect(jsonPath("$.items[2].active").value(true));

        mvc.perform(put("/api/expenses/monthly/" + rent).header("X-User-Id", 1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Flat rent\",\"amount\":13000,\"type\":\"RENT\",\"dueDay\":5,\"note\":\" \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(13000))
                .andExpect(jsonPath("$.note").doesNotExist());

        mvc.perform(patch("/api/expenses/monthly/" + netflix + "/active").header("X-User-Id", 1)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        // Paused items stay in the list (last) but are not counted.
        mvc.perform(get("/api/expenses/monthly").header("X-User-Id", 1))
                .andExpect(jsonPath("$.monthlyTotal").value(15500))
                .andExpect(jsonPath("$.activeCount").value(2))
                .andExpect(jsonPath("$.items[2].name").value("Netflix"));

        mvc.perform(delete("/api/expenses/monthly/" + netflix).header("X-User-Id", 1))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/expenses/monthly").header("X-User-Id", 1))
                .andExpect(jsonPath("$.items.length()").value(2));
    }

    @Test
    void usersOnlySeeAndChangeTheirOwn() throws Exception {
        long rent = create(1, "{\"name\":\"Rent\",\"amount\":12000,\"type\":\"RENT\"}");

        mvc.perform(get("/api/expenses/monthly").header("X-User-Id", 2))
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.monthlyTotal").value(0));
        mvc.perform(delete("/api/expenses/monthly/" + rent).header("X-User-Id", 2))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/expenses/monthly/" + rent).header("X-User-Id", 2)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"x\",\"amount\":1}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidInputIsRejected() throws Exception {
        mvc.perform(post("/api/expenses/monthly").header("X-User-Id", 1).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"amount\":0,\"dueDay\":32,\"note\":\"" + "x".repeat(501) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.amount").exists())
                .andExpect(jsonPath("$.errors.dueDay").exists())
                .andExpect(jsonPath("$.errors.note").exists());
        mvc.perform(post("/api/expenses/monthly").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Rent\",\"amount\":1}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void activeMonthlyExpensesComeOutOfTheDailyBudget() throws Exception {
        create(1, "{\"name\":\"Rent\",\"amount\":12000,\"type\":\"RENT\"}");
        create(1, "{\"name\":\"Electricity\",\"amount\":4000,\"type\":\"UTILITIES\"}");
        long paused = create(1, "{\"name\":\"Gym\",\"amount\":1500,\"active\":false}");

        // 62000 - 15000 EMI - 16000 monthly expenses = 31000 -> 1000/day over 31 days.
        mvc.perform(get("/api/expenses/daily").header("X-User-Id", 1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monthlyExpenses").value(16000))
                .andExpect(jsonPath("$.spendableThisMonth").value(31000))
                .andExpect(jsonPath("$.dailyBudget").value(1000.00));

        mvc.perform(patch("/api/expenses/monthly/" + paused + "/active").header("X-User-Id", 1)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"active\":true}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/expenses/daily").header("X-User-Id", 1))
                .andExpect(jsonPath("$.monthlyExpenses").value(17500))
                .andExpect(jsonPath("$.spendableThisMonth").value(29500));
    }

    @Test
    void internalMonthTotalIncludesActiveMonthlyExpenses() throws Exception {
        create(1, "{\"name\":\"Rent\",\"amount\":12000,\"type\":\"RENT\"}");
        create(1, "{\"name\":\"Gym\",\"amount\":1500,\"active\":false}");
        mvc.perform(post("/api/expenses").header("X-User-Id", 1).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryId\":2,\"amount\":800,\"expenseDate\":\"2026-10-03\"}"))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/expenses/total").param("userId", "1").param("month", "10").param("year", "2026"))
                .andExpect(status().isOk())
                .andExpect(content().string("12800.00"));
    }

    private long create(long userId, String body) throws Exception {
        String res = mvc.perform(post("/api/expenses/monthly").header("X-User-Id", userId)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode node = json.readTree(res);
        return node.get("monthlyExpenseId").asLong();
    }
}
