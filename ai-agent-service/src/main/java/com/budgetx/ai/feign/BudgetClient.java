package com.budgetx.ai.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "BUDGET-SERVICE")
public interface BudgetClient {

    @GetMapping("/api/budgets/summary")
    BudgetSummary getSummary(@RequestParam("userId") Long userId);
}
