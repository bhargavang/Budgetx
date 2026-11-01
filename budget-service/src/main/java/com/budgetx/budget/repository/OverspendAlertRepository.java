package com.budgetx.budget.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.budgetx.budget.entity.OverspendAlert;

public interface OverspendAlertRepository extends JpaRepository<OverspendAlert, Long> {

    boolean existsByUserIdAndAlertYearAndAlertMonth(Long userId, int alertYear, int alertMonth);
}
