package com.budgetx.emi.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.budgetx.emi.entity.Emi;
import com.budgetx.emi.entity.EmiStatus;
import com.budgetx.emi.entity.LoanType;
import com.budgetx.emi.service.EmiCalculator;

public record EmiResponse(
        Long emiId,
        String loanName,
        String lenderName,
        LoanType loanType,
        BigDecimal principalAmount,
        BigDecimal interestRate,
        Integer tenureMonths,
        BigDecimal emiAmount,
        LocalDate startDate,
        LocalDate endDate,
        EmiStatus status,
        int instalmentsPaid,
        int instalmentsRemaining,
        BigDecimal outstandingPrincipal) {

    public static EmiResponse from(Emi emi, LocalDate asOf) {
        int paid = emi.getStatus() == EmiStatus.CLOSED
                ? emi.getTenureMonths()
                : EmiCalculator.instalmentsPaid(emi.getStartDate(), emi.getTenureMonths(), asOf);
        BigDecimal outstanding = emi.getStatus() == EmiStatus.CLOSED
                ? BigDecimal.ZERO.setScale(2)
                : EmiCalculator.outstandingPrincipal(
                        emi.getPrincipalAmount(), emi.getInterestRate(), emi.getEmiAmount(), paid);
        return new EmiResponse(
                emi.getEmiId(),
                emi.getLoanName(),
                emi.getLenderName(),
                emi.getLoanType(),
                emi.getPrincipalAmount(),
                emi.getInterestRate(),
                emi.getTenureMonths(),
                emi.getEmiAmount(),
                emi.getStartDate(),
                emi.getEndDate(),
                emi.getStatus(),
                paid,
                emi.getTenureMonths() - paid,
                outstanding);
    }
}
