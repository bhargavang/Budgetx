package com.budgetx.expense.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.budgetx.expense.entity.ExpenseCategory;

public interface ExpenseCategoryRepository extends JpaRepository<ExpenseCategory, Long> {

    List<ExpenseCategory> findAllByOrderByNameAsc();
}
