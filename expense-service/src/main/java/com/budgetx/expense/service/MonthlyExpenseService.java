package com.budgetx.expense.service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.budgetx.expense.dto.MonthlyExpenseRequest;
import com.budgetx.expense.dto.MonthlyExpenseResponse;
import com.budgetx.expense.dto.MonthlyExpensesResponse;
import com.budgetx.expense.entity.MonthlyExpense;
import com.budgetx.expense.entity.MonthlyExpenseType;
import com.budgetx.expense.exception.ResourceNotFoundException;
import com.budgetx.expense.repository.MonthlyExpenseRepository;

import lombok.RequiredArgsConstructor;

/** Fixed costs the user pays every month (rent, bills, subscriptions), with a note on each. */
@Service
@RequiredArgsConstructor
public class MonthlyExpenseService {

    /** Active first, then by due day (no due day last), then biggest first. */
    private static final Comparator<MonthlyExpense> ORDER = Comparator
            .comparing((MonthlyExpense m) -> !m.isActive())
            .thenComparing(MonthlyExpense::getDueDay, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(MonthlyExpense::getAmount, Comparator.reverseOrder());

    private final MonthlyExpenseRepository repository;

    @Transactional(readOnly = true)
    public MonthlyExpensesResponse list(Long userId) {
        List<MonthlyExpense> all = repository.findByUserId(userId).stream().sorted(ORDER).toList();
        List<MonthlyExpense> active = all.stream().filter(MonthlyExpense::isActive).toList();
        BigDecimal total = active.stream().map(MonthlyExpense::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new MonthlyExpensesResponse(total, active.size(),
                all.stream().map(MonthlyExpenseResponse::from).toList());
    }

    /** Sum of the active monthly expenses, used by the daily tracker. */
    @Transactional(readOnly = true)
    public BigDecimal activeTotal(Long userId) {
        BigDecimal sum = repository.sumActive(userId);
        return sum != null ? sum : BigDecimal.ZERO;
    }

    @Transactional
    public MonthlyExpenseResponse create(Long userId, MonthlyExpenseRequest request) {
        MonthlyExpense entity = MonthlyExpense.builder().userId(userId).build();
        apply(entity, request);
        return MonthlyExpenseResponse.from(repository.save(entity));
    }

    @Transactional
    public MonthlyExpenseResponse update(Long userId, Long id, MonthlyExpenseRequest request) {
        MonthlyExpense entity = owned(userId, id);
        apply(entity, request);
        return MonthlyExpenseResponse.from(repository.save(entity));
    }

    @Transactional
    public MonthlyExpenseResponse setActive(Long userId, Long id, boolean active) {
        MonthlyExpense entity = owned(userId, id);
        entity.setActive(active);
        return MonthlyExpenseResponse.from(repository.save(entity));
    }

    @Transactional
    public void delete(Long userId, Long id) {
        repository.delete(owned(userId, id));
    }

    private MonthlyExpense owned(Long userId, Long id) {
        return repository.findByMonthlyExpenseIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Monthly expense not found: " + id));
    }

    private static void apply(MonthlyExpense entity, MonthlyExpenseRequest request) {
        entity.setName(request.name().strip());
        entity.setAmount(request.amount());
        entity.setType(request.type() != null ? request.type() : MonthlyExpenseType.OTHER);
        entity.setDueDay(request.dueDay());
        entity.setNote(request.note() == null || request.note().isBlank() ? null : request.note().strip());
        if (request.active() != null) {
            entity.setActive(request.active());
        }
    }
}
