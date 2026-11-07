package com.budgetx.budget.feign;

import java.math.BigDecimal;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "EMI-SERVICE")
public interface EmiClient {

    @GetMapping("/api/emis/total")
    BigDecimal getTotalEmi(@RequestParam("userId") Long userId,
                           @RequestParam("month") int month,
                           @RequestParam("year") int year);
}
