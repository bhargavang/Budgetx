package com.budgetx.budget.kafka;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.YearMonth;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import com.budgetx.budget.dto.BudgetSummary;
import com.budgetx.budget.dto.ExpenseLoggedEvent;
import com.budgetx.budget.dto.OverspendEvent;
import com.budgetx.budget.entity.OverspendAlert;
import com.budgetx.budget.repository.OverspendAlertRepository;
import com.budgetx.budget.service.BudgetService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Re-checks the month each time an expense is logged and publishes {@code budget.overspend} the first
 * time EMIs + expenses go over the salary. Failures are logged and never block expense logging.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OverspendDetector {

    private final BudgetService budgetService;
    private final OverspendAlertRepository alerts;
    private final KafkaTemplate<String, OverspendEvent> kafkaTemplate;
    private final Clock clock;

    @KafkaListener(topics = KafkaTopics.EXPENSE_LOGGED, groupId = "budget-service")
    public void onExpenseLogged(ExpenseLoggedEvent event) {
        if (event == null || event.userId() == null) {
            return;
        }
        YearMonth period = event.expenseDate() != null ? YearMonth.from(event.expenseDate()) : YearMonth.now(clock);
        try {
            check(event.userId(), period);
        } catch (RuntimeException e) {
            log.warn("Overspend check failed for user {} {}", event.userId(), period, e);
        }
    }

    void check(Long userId, YearMonth period) {
        if (alerts.existsByUserIdAndAlertYearAndAlertMonth(userId, period.getYear(), period.getMonthValue())) {
            return;
        }
        BudgetSummary b = budgetService.forMonth(userId, period);
        if (!b.overspent()) {
            return;
        }
        var overBy = b.totalEmi().add(b.spentThisMonth()).subtract(b.monthlySalary());
        try {
            alerts.saveAndFlush(OverspendAlert.builder().userId(userId).alertYear(period.getYear())
                    .alertMonth(period.getMonthValue()).overBy(overBy).createdAt(LocalDateTime.now(clock)).build());
        } catch (DataIntegrityViolationException duplicate) {
            return; // another consumer already alerted for this month
        }
        kafkaTemplate.send(KafkaTopics.OVERSPEND, userId.toString(), new OverspendEvent(userId, period.getMonthValue(),
                period.getYear(), b.monthlySalary(), b.totalEmi(), b.spentThisMonth(), overBy));
    }
}
