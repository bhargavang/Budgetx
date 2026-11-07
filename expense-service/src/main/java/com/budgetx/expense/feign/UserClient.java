package com.budgetx.expense.feign;

import java.math.BigDecimal;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/** Internal user-service endpoint (blocked at the gateway for external clients). */
@FeignClient(name = "USER-SERVICE")
public interface UserClient {

    @GetMapping("/api/users/{id}/salary")
    BigDecimal getSalary(@PathVariable("id") Long id);
}
