package com.budgetx.emi.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.budgetx.emi.repository.EmiRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class EmiControllerIntegrationTest {

    /** "Today" is 2026-10-15 for every test. */
    @TestConfiguration
    static class FixedClock {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-10-15T10:00:00Z"), ZoneOffset.UTC);
        }
    }

    private static final String HOME_LOAN = """
            {"loanName":"Home loan","lenderName":"SBI","loanType":"HOME",
             "principalAmount":1000000,"interestRate":8.5,"tenureMonths":240,"startDate":"2026-01-05"}
            """;
    private static final String CAR_LOAN = """
            {"loanName":"Car","loanType":"CAR","principalAmount":600000,"interestRate":9,
             "tenureMonths":60,"emiAmount":12500,"startDate":"2026-11-10"}
            """;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EmiRepository emiRepository;

    @BeforeEach
    void clean() {
        emiRepository.deleteAll();
    }

    @Test
    void createComputesEmiAndProgress() throws Exception {
        mvc.perform(post("/api/emis").header("X-User-Id", 1).contentType(MediaType.APPLICATION_JSON).content(HOME_LOAN))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.emiAmount").value(8678.23))
                .andExpect(jsonPath("$.endDate").value("2045-12-05"))
                .andExpect(jsonPath("$.instalmentsPaid").value(10))
                .andExpect(jsonPath("$.instalmentsRemaining").value(230))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void totalOnlyCountsActiveLoansDueThatMonth() throws Exception {
        long homeId = create(1, HOME_LOAN);
        create(1, CAR_LOAN);
        create(2, HOME_LOAN);

        // October 2026: car loan hasn't started yet.
        mvc.perform(get("/api/emis/total").param("userId", "1"))
                .andExpect(status().isOk())
                .andExpect(content().string("8678.23"));
        // December 2026: both loans are due.
        mvc.perform(get("/api/emis/total").param("userId", "1").param("month", "12").param("year", "2026"))
                .andExpect(content().string("21178.23"));

        mvc.perform(patch("/api/emis/{id}/close", homeId).header("X-User-Id", 1))
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.outstandingPrincipal").value(0));
        mvc.perform(get("/api/emis/total").param("userId", "1").param("month", "12").param("year", "2026"))
                .andExpect(content().string("12500.00"));
    }

    @Test
    void usersCannotSeeEachOthersLoans() throws Exception {
        long id = create(1, HOME_LOAN);

        mvc.perform(get("/api/emis/{id}", id).header("X-User-Id", 2)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/emis/{id}", id).header("X-User-Id", 2)).andExpect(status().isNotFound());
        mvc.perform(get("/api/emis").header("X-User-Id", 2))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void summaryAggregatesActiveLoans() throws Exception {
        create(1, HOME_LOAN);
        create(1, CAR_LOAN);

        mvc.perform(get("/api/emis/summary").header("X-User-Id", 1))
                .andExpect(jsonPath("$.activeLoans").value(2))
                .andExpect(jsonPath("$.monthlyEmiTotal").value(8678.23));
    }

    @Test
    void missingUserHeaderIsUnauthorized() throws Exception {
        mvc.perform(get("/api/emis")).andExpect(status().isUnauthorized());
    }

    @Test
    void invalidRequestReturnsFieldErrors() throws Exception {
        mvc.perform(post("/api/emis").header("X-User-Id", 1).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loanName\":\"\",\"principalAmount\":-5,\"tenureMonths\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.loanName").exists())
                .andExpect(jsonPath("$.errors.principalAmount").exists())
                .andExpect(jsonPath("$.errors.tenureMonths").exists())
                .andExpect(jsonPath("$.errors.loanType").exists());
    }

    private long create(long userId, String body) throws Exception {
        String json = mvc.perform(post("/api/emis").header("X-User-Id", userId)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("emiId").asLong();
    }
}
