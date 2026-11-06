package com.budgetx.expense.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** How much of the salary the user wants to keep aside each month before the daily budget is worked out. */
@Entity
@Table(name = "daily_budget_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DailyBudgetSettings {

    @Id
    private Long userId;

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal monthlySavingsGoal = BigDecimal.ZERO;
}
