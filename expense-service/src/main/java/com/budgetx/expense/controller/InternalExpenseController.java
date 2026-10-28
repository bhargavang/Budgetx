package com.budgetx.expense.controller;

import java.math.BigDecimal;
import java.time.YearMonth;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.budgetx.expense.service.ExpenseService;

import lombok.RequiredArgsConstructor;

/** Service-to-service endpoint for budget-service (Feign). The API gateway blocks it externally. */
@RestController
@RequiredArgsConstructor
public class InternalExpenseController {

    private final ExpenseService expenseService;

    @GetMapping("/api/expenses/total")
    public BigDecimal total(@RequestParam Long userId, @RequestParam int month, @RequestParam int year) {
        return expenseService.totalForMonth(userId, YearMonth.of(year, month));
    }
}
