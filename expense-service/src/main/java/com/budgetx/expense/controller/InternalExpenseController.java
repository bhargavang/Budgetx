package com.budgetx.expense.controller;

import java.math.BigDecimal;
import java.time.YearMonth;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.budgetx.expense.service.ExpenseService;
import com.budgetx.expense.service.MonthlyExpenseService;

import lombok.RequiredArgsConstructor;

/** Service-to-service endpoint for budget-service (Feign). The API gateway blocks it externally. */
@RestController
@RequiredArgsConstructor
public class InternalExpenseController {

    private final ExpenseService expenseService;
    private final MonthlyExpenseService monthlyExpenseService;

    /**
     * Logged expenses for the month plus the user's active fixed monthly expenses (rent, bills...), which
     * are not logged day by day. Fixed costs are not kept per month, so today's list is used for every
     * month; month-on-month comparisons stay fair because the same amount is added to both.
     */
    @GetMapping("/api/expenses/total")
    public BigDecimal total(@RequestParam Long userId, @RequestParam int month, @RequestParam int year) {
        return expenseService.totalForMonth(userId, YearMonth.of(year, month))
                .add(monthlyExpenseService.activeTotal(userId));
    }
}
