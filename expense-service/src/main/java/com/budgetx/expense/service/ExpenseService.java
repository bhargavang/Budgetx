package com.budgetx.expense.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.budgetx.expense.dto.CategorySummary;
import com.budgetx.expense.dto.ExpenseEvent;
import com.budgetx.expense.dto.ExpenseRequest;
import com.budgetx.expense.dto.ExpenseResponse;
import com.budgetx.expense.dto.MonthSummaryResponse;
import com.budgetx.expense.dto.MonthlySummary;
import com.budgetx.expense.entity.Expense;
import com.budgetx.expense.entity.ExpenseCategory;
import com.budgetx.expense.exception.ResourceNotFoundException;
import com.budgetx.expense.repository.ExpenseCategoryRepository;
import com.budgetx.expense.repository.ExpenseRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final ExpenseCategoryRepository categoryRepository;
    private final ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public List<ExpenseCategory> categories() {
        return categoryRepository.findAllByOrderByNameAsc();
    }

    @Transactional
    public ExpenseResponse create(Long userId, ExpenseRequest request) {
        Expense expense = Expense.builder().userId(userId).build();
        apply(expense, request);
        Expense saved = expenseRepository.save(expense);
        events.publishEvent(new ExpenseEvent(userId, saved.getExpenseId(), saved.getAmount(),
                saved.getCategory().getName(), saved.getExpenseDate()));
        return ExpenseResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public Page<ExpenseResponse> search(Long userId, LocalDate from, LocalDate to, Long categoryId, Pageable pageable) {
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("'from' must not be after 'to'");
        }
        return expenseRepository.search(userId, from, to, categoryId, pageable).map(ExpenseResponse::from);
    }

    @Transactional(readOnly = true)
    public ExpenseResponse get(Long userId, Long expenseId) {
        return ExpenseResponse.from(find(userId, expenseId));
    }

    @Transactional
    public ExpenseResponse update(Long userId, Long expenseId, ExpenseRequest request) {
        Expense expense = find(userId, expenseId);
        apply(expense, request);
        return ExpenseResponse.from(expense);
    }

    @Transactional
    public void delete(Long userId, Long expenseId) {
        expenseRepository.delete(find(userId, expenseId));
    }

    @Transactional(readOnly = true)
    public BigDecimal totalForMonth(Long userId, YearMonth month) {
        return expenseRepository.sumBetween(userId, month.atDay(1), month.atEndOfMonth());
    }

    @Transactional(readOnly = true)
    public MonthSummaryResponse monthSummary(Long userId, YearMonth month) {
        List<CategorySummary> byCategory =
                expenseRepository.sumByCategory(userId, month.atDay(1), month.atEndOfMonth());
        BigDecimal essential = BigDecimal.ZERO;
        BigDecimal discretionary = BigDecimal.ZERO;
        for (CategorySummary c : byCategory) {
            if (c.essential()) {
                essential = essential.add(c.total());
            } else {
                discretionary = discretionary.add(c.total());
            }
        }
        return new MonthSummaryResponse(month.getMonthValue(), month.getYear(),
                essential.add(discretionary), essential, discretionary, byCategory);
    }

    @Transactional(readOnly = true)
    public List<MonthlySummary> yearSummary(Long userId, int year) {
        return expenseRepository.sumByMonth(userId, year);
    }

    private void apply(Expense expense, ExpenseRequest request) {
        ExpenseCategory category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown categoryId " + request.categoryId()));
        expense.setCategory(category);
        expense.setAmount(request.amount());
        expense.setDescription(request.description());
        expense.setExpenseDate(request.expenseDate());
        expense.setPaymentMethod(request.paymentMethod());
    }

    private Expense find(Long userId, Long expenseId) {
        return expenseRepository.findByExpenseIdAndUserId(expenseId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Expense " + expenseId + " not found"));
    }
}
