package com.budgetx.expense.dto;

import java.math.BigDecimal;

import com.budgetx.expense.entity.MonthlyExpense;
import com.budgetx.expense.entity.MonthlyExpenseType;

public record MonthlyExpenseResponse(
        Long monthlyExpenseId,
        String name,
        BigDecimal amount,
        MonthlyExpenseType type,
        Integer dueDay,
        String note,
        boolean active) {

    public static MonthlyExpenseResponse from(MonthlyExpense m) {
        return new MonthlyExpenseResponse(m.getMonthlyExpenseId(), m.getName(), m.getAmount(), m.getType(),
                m.getDueDay(), m.getNote(), m.isActive());
    }
}
