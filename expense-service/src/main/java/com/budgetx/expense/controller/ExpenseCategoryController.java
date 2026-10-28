package com.budgetx.expense.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.budgetx.expense.entity.ExpenseCategory;
import com.budgetx.expense.service.ExpenseService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class ExpenseCategoryController {

    private final ExpenseService expenseService;

    @GetMapping("/api/expense-categories")
    public List<ExpenseCategory> categories() {
        return expenseService.categories();
    }
}
