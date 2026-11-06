package com.budgetx.expense.feign;

import java.math.BigDecimal;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Internal emi-service endpoint (blocked at the gateway for external clients). */
@FeignClient(name = "EMI-SERVICE")
public interface EmiClient {

    @GetMapping("/api/emis/total")
    BigDecimal getTotalEmi(@RequestParam("userId") Long userId,
                           @RequestParam("month") int month,
                           @RequestParam("year") int year);
}
