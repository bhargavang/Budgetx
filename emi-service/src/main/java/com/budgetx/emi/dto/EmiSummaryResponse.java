package com.budgetx.emi.dto;

import java.math.BigDecimal;

public record EmiSummaryResponse(
        int activeLoans,
        BigDecimal monthlyEmiTotal,
        BigDecimal totalOutstandingPrincipal) {
}
