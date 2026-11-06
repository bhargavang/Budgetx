package com.budgetx.expense.controller;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.budgetx.expense.dto.DailyBudgetSettingsRequest;
import com.budgetx.expense.dto.DailySummaryResponse;
import com.budgetx.expense.dto.DayNoteRequest;
import com.budgetx.expense.service.DailyTrackerService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Salary-based daily budget: what the user can spend today, what they spent, and a note for the day. */
@RestController
@RequestMapping("/api/expenses/daily")
@RequiredArgsConstructor
public class DailyTrackerController {

    private static final String USER_ID = ExpenseController.USER_ID;

    private final DailyTrackerService trackerService;
    private final Clock clock;

    /** Defaults to today. */
    @GetMapping
    public DailySummaryResponse summary(@RequestHeader(USER_ID) Long userId,
                                        @RequestParam(required = false)
                                        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return trackerService.summary(userId, date != null ? date : LocalDate.now(clock));
    }

    @PutMapping("/{date}/note")
    public Map<String, String> saveNote(@RequestHeader(USER_ID) Long userId,
                                        @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                        @Valid @RequestBody DayNoteRequest request) {
        String note = trackerService.saveDayNote(userId, date, request.note());
        return Collections.singletonMap("note", note);
    }

    @GetMapping("/settings")
    public Map<String, BigDecimal> settings(@RequestHeader(USER_ID) Long userId) {
        return Map.of("monthlySavingsGoal", trackerService.savingsGoal(userId));
    }

    @PutMapping("/settings")
    public Map<String, BigDecimal> saveSettings(@RequestHeader(USER_ID) Long userId,
                                                @Valid @RequestBody DailyBudgetSettingsRequest request) {
        return Map.of("monthlySavingsGoal", trackerService.saveSavingsGoal(userId, request.monthlySavingsGoal()));
    }
}
