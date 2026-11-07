package com.budgetx.expense.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.budgetx.expense.dto.MonthlyExpenseRequest;
import com.budgetx.expense.dto.MonthlyExpenseResponse;
import com.budgetx.expense.dto.MonthlyExpensesResponse;
import com.budgetx.expense.service.MonthlyExpenseService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** Recurring monthly costs such as rent, bills and subscriptions. */
@RestController
@RequestMapping("/api/expenses/monthly")
@RequiredArgsConstructor
public class MonthlyExpenseController {

    private static final String USER_ID = ExpenseController.USER_ID;

    private final MonthlyExpenseService service;

    @GetMapping
    public MonthlyExpensesResponse list(@RequestHeader(USER_ID) Long userId) {
        return service.list(userId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MonthlyExpenseResponse create(@RequestHeader(USER_ID) Long userId,
                                         @Valid @RequestBody MonthlyExpenseRequest request) {
        return service.create(userId, request);
    }

    @PutMapping("/{id}")
    public MonthlyExpenseResponse update(@RequestHeader(USER_ID) Long userId, @PathVariable Long id,
                                         @Valid @RequestBody MonthlyExpenseRequest request) {
        return service.update(userId, id, request);
    }

    /** Pause or resume: body {"active": false}. */
    @PatchMapping("/{id}/active")
    public MonthlyExpenseResponse setActive(@RequestHeader(USER_ID) Long userId, @PathVariable Long id,
                                            @RequestBody Map<String, Boolean> body) {
        Boolean active = body.get("active");
        if (active == null) {
            throw new IllegalArgumentException("active is required");
        }
        return service.setActive(userId, id, active);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@RequestHeader(USER_ID) Long userId, @PathVariable Long id) {
        service.delete(userId, id);
    }
}
