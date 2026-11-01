package com.budgetx.budget.feign;

import java.math.BigDecimal;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "EXPENSE-SERVICE")
public interface ExpenseClient {

    @GetMapping("/api/expenses/total")
    BigDecimal getTotalExpenses(@RequestParam("userId") Long userId,
                                @RequestParam("month") int month,
                                @RequestParam("year") int year);
}
