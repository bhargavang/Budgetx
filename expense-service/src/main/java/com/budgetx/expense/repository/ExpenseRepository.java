package com.budgetx.expense.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.budgetx.expense.dto.CategorySummary;
import com.budgetx.expense.dto.MonthlySummary;
import com.budgetx.expense.entity.Expense;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    @EntityGraph(attributePaths = "category")
    Optional<Expense> findByExpenseIdAndUserId(Long expenseId, Long userId);

    @EntityGraph(attributePaths = "category")
    List<Expense> findByUserIdAndExpenseDateOrderByCreatedAtAsc(Long userId, LocalDate expenseDate);

    @EntityGraph(attributePaths = "category")
    @Query("""
            select e from Expense e
            where e.userId = :userId
              and e.expenseDate between :from and :to
              and (:categoryId is null or e.category.categoryId = :categoryId)
            """)
    Page<Expense> search(@Param("userId") Long userId,
                         @Param("from") LocalDate from,
                         @Param("to") LocalDate to,
                         @Param("categoryId") Long categoryId,
                         Pageable pageable);

    @Query("""
            select coalesce(sum(e.amount), 0) from Expense e
            where e.userId = :userId and e.expenseDate between :from and :to
            """)
    BigDecimal sumBetween(@Param("userId") Long userId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("""
            select new com.budgetx.expense.dto.CategorySummary(
                c.categoryId, c.name, c.essential, sum(e.amount), count(e))
            from Expense e join e.category c
            where e.userId = :userId and e.expenseDate between :from and :to
            group by c.categoryId, c.name, c.essential
            order by sum(e.amount) desc
            """)
    List<CategorySummary> sumByCategory(@Param("userId") Long userId,
                                        @Param("from") LocalDate from,
                                        @Param("to") LocalDate to);

    @Query("""
            select new com.budgetx.expense.dto.MonthlySummary(month(e.expenseDate), sum(e.amount), count(e))
            from Expense e
            where e.userId = :userId and year(e.expenseDate) = :year
            group by month(e.expenseDate)
            order by month(e.expenseDate)
            """)
    List<MonthlySummary> sumByMonth(@Param("userId") Long userId, @Param("year") int year);
}
