package com.budgetx.emi.controller;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.YearMonth;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.budgetx.emi.service.EmiService;

import lombok.RequiredArgsConstructor;

/** Service-to-service endpoint for budget-service (Feign). The API gateway blocks it externally. */
@RestController
@RequiredArgsConstructor
public class InternalEmiController {

    private final EmiService emiService;
    private final Clock clock;

    /** Total EMI due for the month; defaults to the current month. */
    @GetMapping("/api/emis/total")
    public BigDecimal total(@RequestParam Long userId,
                            @RequestParam(required = false) Integer month,
                            @RequestParam(required = false) Integer year) {
        YearMonth now = YearMonth.now(clock);
        YearMonth period = YearMonth.of(year != null ? year : now.getYear(), month != null ? month : now.getMonthValue());
        return emiService.totalForMonth(userId, period);
    }
}
