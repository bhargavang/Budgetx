package com.budgetx.budget.dto;

import java.math.BigDecimal;

/** Published to {@code budget.overspend} the first time a user's month goes over their salary. */
public record OverspendEvent(Long userId, int month, int year, BigDecimal monthlySalary,
                             BigDecimal totalEmi, BigDecimal totalExpenses, BigDecimal overBy) {
}
