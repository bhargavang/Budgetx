package com.budgetx.expense.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.budgetx.expense.entity.MonthlyExpense;

public interface MonthlyExpenseRepository extends JpaRepository<MonthlyExpense, Long> {

    List<MonthlyExpense> findByUserId(Long userId);

    Optional<MonthlyExpense> findByMonthlyExpenseIdAndUserId(Long monthlyExpenseId, Long userId);

    @Query("""
            select coalesce(sum(m.amount), 0) from MonthlyExpense m
            where m.userId = :userId and m.active = true
            """)
    BigDecimal sumActive(@Param("userId") Long userId);
}
