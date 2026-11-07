package com.budgetx.expense.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.budgetx.expense.entity.DailyBudgetSettings;

public interface DailyBudgetSettingsRepository extends JpaRepository<DailyBudgetSettings, Long> {
}
