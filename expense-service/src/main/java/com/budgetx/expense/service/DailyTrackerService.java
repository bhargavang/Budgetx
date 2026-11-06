package com.budgetx.expense.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.budgetx.expense.dto.DailySummaryResponse;
import com.budgetx.expense.dto.ExpenseResponse;
import com.budgetx.expense.entity.DailyBudgetSettings;
import com.budgetx.expense.entity.DayNote;
import com.budgetx.expense.exception.UpstreamUnavailableException;
import com.budgetx.expense.feign.EmiClient;
import com.budgetx.expense.feign.UserClient;
import com.budgetx.expense.repository.DailyBudgetSettingsRepository;
import com.budgetx.expense.repository.DayNoteRepository;
import com.budgetx.expense.repository.ExpenseRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Turns the user's salary into a daily spending budget and tracks each day's expenses and note against it. */
@Slf4j
@Service
@RequiredArgsConstructor
public class DailyTrackerService {

    private final ExpenseRepository expenseRepository;
    private final DayNoteRepository dayNoteRepository;
    private final DailyBudgetSettingsRepository settingsRepository;
    private final UserClient userClient;
    private final EmiClient emiClient;
    private final Clock clock;

    @Transactional(readOnly = true)
    public DailySummaryResponse summary(Long userId, LocalDate date) {
        LocalDate today = LocalDate.now(clock);
        if (date.isAfter(today)) {
            throw new IllegalArgumentException("date must not be in the future");
        }
        YearMonth month = YearMonth.from(date);

        BigDecimal salary = salary(userId);
        BigDecimal emi = BigDecimal.ZERO;
        boolean emiAvailable = true;
        try {
            emi = nonNull(emiClient.getTotalEmi(userId, month.getMonthValue(), month.getYear()));
        } catch (RuntimeException ex) {
            // The tracker still works without EMI data; the client is told the figure is missing.
            log.warn("emi-service unavailable for user {}: {}", userId, ex.getMessage());
            emiAvailable = false;
        }
        BigDecimal savingsGoal = savingsGoal(userId);
        BigDecimal spendable = salary.subtract(emi).subtract(savingsGoal).max(BigDecimal.ZERO);

        int daysInMonth = month.lengthOfMonth();
        int daysLeft = daysInMonth - date.getDayOfMonth() + 1;
        BigDecimal dailyBudget = divide(spendable, daysInMonth);

        BigDecimal spentBefore = date.getDayOfMonth() == 1 ? BigDecimal.ZERO
                : expenseRepository.sumBetween(userId, month.atDay(1), date.minusDays(1));
        BigDecimal allowance = divide(spendable.subtract(spentBefore).max(BigDecimal.ZERO), daysLeft);

        List<ExpenseResponse> expenses = expenseRepository
                .findByUserIdAndExpenseDateOrderByCreatedAtAsc(userId, date).stream()
                .map(ExpenseResponse::from).toList();
        BigDecimal spentToday = expenses.stream().map(ExpenseResponse::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal spentMonth = expenseRepository.sumBetween(userId, month.atDay(1), month.atEndOfMonth());

        String note = dayNoteRepository.findByUserIdAndNoteDate(userId, date).map(DayNote::getNote).orElse(null);

        return new DailySummaryResponse(date, salary, emi, emiAvailable, savingsGoal, spendable,
                daysInMonth, daysLeft, dailyBudget, allowance, spentToday, allowance.subtract(spentToday),
                spentMonth, spendable.subtract(spentMonth), note, expenses);
    }

    /** Saves the note for a day; a blank note deletes it. Returns the stored note (or null). */
    @Transactional
    public String saveDayNote(Long userId, LocalDate date, String note) {
        if (date.isAfter(LocalDate.now(clock))) {
            throw new IllegalArgumentException("date must not be in the future");
        }
        var existing = dayNoteRepository.findByUserIdAndNoteDate(userId, date);
        if (note == null || note.isBlank()) {
            existing.ifPresent(dayNoteRepository::delete);
            return null;
        }
        DayNote dayNote = existing.orElseGet(() -> DayNote.builder().userId(userId).noteDate(date).build());
        dayNote.setNote(note.strip());
        return dayNoteRepository.save(dayNote).getNote();
    }

    @Transactional(readOnly = true)
    public BigDecimal savingsGoal(Long userId) {
        return settingsRepository.findById(userId).map(DailyBudgetSettings::getMonthlySavingsGoal)
                .orElse(BigDecimal.ZERO);
    }

    @Transactional
    public BigDecimal saveSavingsGoal(Long userId, BigDecimal goal) {
        DailyBudgetSettings settings = settingsRepository.findById(userId)
                .orElseGet(() -> DailyBudgetSettings.builder().userId(userId).build());
        settings.setMonthlySavingsGoal(goal);
        return settingsRepository.save(settings).getMonthlySavingsGoal();
    }

    private BigDecimal salary(Long userId) {
        try {
            return nonNull(userClient.getSalary(userId));
        } catch (RuntimeException ex) {
            throw new UpstreamUnavailableException("Could not load your salary right now. Please try again shortly.", ex);
        }
    }

    private static BigDecimal divide(BigDecimal amount, int days) {
        return amount.divide(BigDecimal.valueOf(days), 2, RoundingMode.DOWN);
    }

    private static BigDecimal nonNull(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
