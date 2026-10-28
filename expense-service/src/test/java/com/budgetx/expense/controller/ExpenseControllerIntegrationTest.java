package com.budgetx.expense.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.test.web.servlet.MockMvc;

import com.budgetx.expense.dto.ExpenseEvent;
import com.budgetx.expense.kafka.KafkaTopics;
import com.budgetx.expense.repository.ExpenseRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class ExpenseControllerIntegrationTest {

    /** "Today" is 2026-10-15 for every test. */
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
    private ObjectMapper objectMapper;

    @Autowired
    private ExpenseRepository expenseRepository;

    @MockBean
    private KafkaTemplate<String, ExpenseEvent> kafkaTemplate;

    @BeforeEach
    void setUp() {
        expenseRepository.deleteAll();
        when(kafkaTemplate.send(anyString(), anyString(), any())).thenReturn(new CompletableFuture<>());
    }

    @Test
    void categoriesAreSeeded() throws Exception {
        mvc.perform(get("/api/expense-categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(15))
                .andExpect(jsonPath("$[?(@.name == 'Groceries')].essential").value(true));
    }

    @Test
    void createPublishesKafkaEvent() throws Exception {
        long id = create(1, 2, "1250.50", "2026-10-03");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<ExpenseEvent> event = ArgumentCaptor.forClass(ExpenseEvent.class);
        verify(kafkaTemplate).send(eq(KafkaTopics.EXPENSE_LOGGED), eq("1"), event.capture());
        assertThat(event.getValue()).isEqualTo(new ExpenseEvent(
                1L, id, new BigDecimal("1250.50"), "Groceries", LocalDate.of(2026, 10, 3)));
        // Consumers in other services parse this JSON, so dates must be ISO strings.
        assertThat(new JsonSerializer<ExpenseEvent>()
                .serialize(KafkaTopics.EXPENSE_LOGGED, event.getValue()))
                .asString().contains("\"expenseDate\":\"2026-10-03\"");
    }

    @Test
    void invalidExpenseIsRejectedAndNotPublished() throws Exception {
        mvc.perform(post("/api/expenses").header("X-User-Id", 1).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryId\":999,\"amount\":10,\"expenseDate\":\"2026-10-01\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/expenses").header("X-User-Id", 1).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryId\":1,\"amount\":0,\"expenseDate\":\"2030-01-01\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.amount").exists())
                .andExpect(jsonPath("$.errors.expenseDate").exists());

        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
    }

    @Test
    void totalAndSummariesGroupByMonthAndCategory() throws Exception {
        create(1, 1, "20000", "2026-10-01");   // Rent (essential)
        create(1, 2, "3000", "2026-10-05");    // Groceries (essential)
        create(1, 9, "1500", "2026-10-07");    // Dining out
        create(1, 9, "500", "2026-10-20");     // Dining out
        create(1, 2, "2800", "2026-09-10");    // September
        create(2, 1, "99999", "2026-10-01");   // someone else

        mvc.perform(get("/api/expenses/total").param("userId", "1").param("month", "10").param("year", "2026"))
                .andExpect(content().string("25000.00"));

        mvc.perform(get("/api/expenses/summary/month").header("X-User-Id", 1))
                .andExpect(jsonPath("$.total").value(25000.00))
                .andExpect(jsonPath("$.essentialTotal").value(23000.00))
                .andExpect(jsonPath("$.discretionaryTotal").value(2000.00))
                .andExpect(jsonPath("$.byCategory[0].categoryName").value("Rent"))
                .andExpect(jsonPath("$.byCategory[2].categoryName").value("Dining Out"))
                .andExpect(jsonPath("$.byCategory[2].count").value(2));

        mvc.perform(get("/api/expenses/summary/year").header("X-User-Id", 1))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].month").value(9))
                .andExpect(jsonPath("$[1].total").value(25000.00));
    }

    @Test
    void listDefaultsToCurrentMonthAndFiltersByCategory() throws Exception {
        create(1, 2, "3000", "2026-10-05");
        create(1, 9, "1500", "2026-10-07");
        create(1, 2, "2800", "2026-09-10");

        mvc.perform(get("/api/expenses").header("X-User-Id", 1))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].expenseDate").value("2026-10-07"));
        mvc.perform(get("/api/expenses").header("X-User-Id", 1)
                        .param("from", "2026-09-01").param("to", "2026-10-31").param("categoryId", "2"))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void updateAndDeleteAreScopedToOwner() throws Exception {
        long id = create(1, 2, "3000", "2026-10-05");
        String update = "{\"categoryId\":9,\"amount\":450,\"expenseDate\":\"2026-10-06\",\"paymentMethod\":\"UPI\"}";

        mvc.perform(put("/api/expenses/{id}", id).header("X-User-Id", 2)
                        .contentType(MediaType.APPLICATION_JSON).content(update))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/expenses/{id}", id).header("X-User-Id", 1)
                        .contentType(MediaType.APPLICATION_JSON).content(update))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoryName").value("Dining Out"))
                .andExpect(jsonPath("$.paymentMethod").value("UPI"));

        mvc.perform(delete("/api/expenses/{id}", id).header("X-User-Id", 2)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/expenses/{id}", id).header("X-User-Id", 1)).andExpect(status().isNoContent());
        mvc.perform(get("/api/expenses/{id}", id).header("X-User-Id", 1)).andExpect(status().isNotFound());
    }

    private long create(long userId, long categoryId, String amount, String date) throws Exception {
        String body = "{\"categoryId\":%d,\"amount\":%s,\"expenseDate\":\"%s\"}".formatted(categoryId, amount, date);
        String json = mvc.perform(post("/api/expenses").header("X-User-Id", userId)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("expenseId").asLong();
    }
}
