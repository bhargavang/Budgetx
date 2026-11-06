package com.budgetx.budget.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.budgetx.budget.dto.BudgetSummary;
import com.budgetx.budget.service.BudgetService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/budgets")
@RequiredArgsConstructor
public class BudgetController {

    private final BudgetService budgetService;

    /** This month's budget for the signed-in user. */
    @GetMapping("/current")
    public BudgetSummary current(@RequestHeader("X-User-Id") Long userId) {
        return budgetService.current(userId);
    }
}
