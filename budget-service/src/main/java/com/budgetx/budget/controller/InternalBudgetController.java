package com.budgetx.budget.controller;

import java.time.Clock;
import java.time.YearMonth;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.budgetx.budget.dto.BudgetSummary;
import com.budgetx.budget.service.BudgetService;

import lombok.RequiredArgsConstructor;

/** Service-to-service endpoint for ai-agent-service (Feign). The API gateway blocks it externally. */
@RestController
@RequiredArgsConstructor
public class InternalBudgetController {

    private final BudgetService budgetService;
    private final Clock clock;

    @GetMapping("/api/budgets/summary")
    public BudgetSummary summary(@RequestParam Long userId,
                                 @RequestParam(required = false) Integer month,
                                 @RequestParam(required = false) Integer year) {
        YearMonth now = YearMonth.now(clock);
        return budgetService.forMonth(userId,
                YearMonth.of(year != null ? year : now.getYear(), month != null ? month : now.getMonthValue()));
    }
}
