package com.budgetx.budget.kafka;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;

import com.budgetx.budget.dto.ExpenseLoggedEvent;
import com.budgetx.budget.dto.OverspendEvent;
import com.budgetx.budget.feign.RiskProfile;
import com.budgetx.budget.repository.OverspendAlertRepository;
import com.budgetx.budget.service.BudgetCalculator;
import com.budgetx.budget.service.BudgetService;

class OverspendDetectorTest {

    private final BudgetService budgetService = mock(BudgetService.class);
    private final OverspendAlertRepository alerts = mock(OverspendAlertRepository.class);
    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, OverspendEvent> kafka = mock(KafkaTemplate.class);
    private final OverspendDetector detector = new OverspendDetector(budgetService, alerts, kafka,
            Clock.fixed(Instant.parse("2026-10-15T10:00:00Z"), ZoneOffset.UTC));
    private final BudgetCalculator calc = new BudgetCalculator();

    private void budget(String salary, String emi, String spent) {
        when(budgetService.forMonth(1L, YearMonth.of(2026, 10))).thenReturn(calc.calculate(1L, 10, 2026,
                new BigDecimal(salary), RiskProfile.MODERATE, new BigDecimal(emi), new BigDecimal(spent), BigDecimal.ZERO));
    }

    private ExpenseLoggedEvent event() {
        return new ExpenseLoggedEvent(1L, 9L, new BigDecimal("500"), "Groceries", LocalDate.of(2026, 10, 14));
    }

    @Test
    void publishesOnceWhenMonthGoesOverSalary() {
        budget("40000", "25000", "20000");

        detector.onExpenseLogged(event());

        ArgumentCaptor<OverspendEvent> sent = ArgumentCaptor.forClass(OverspendEvent.class);
        verify(kafka).send(eq(KafkaTopics.OVERSPEND), eq("1"), sent.capture());
        org.assertj.core.api.Assertions.assertThat(sent.getValue().overBy()).isEqualByComparingTo("5000");
        verify(alerts).saveAndFlush(any());
    }

    @Test
    void staysQuietWithinBudget() {
        budget("80000", "25000", "20000");

        detector.onExpenseLogged(event());

        verify(kafka, never()).send(anyString(), anyString(), any());
    }

    @Test
    void doesNotRepeatAnAlertAlreadySentThisMonth() {
        when(alerts.existsByUserIdAndAlertYearAndAlertMonth(1L, 2026, 10)).thenReturn(true);

        detector.onExpenseLogged(event());

        verify(budgetService, never()).forMonth(any(), any());
        verify(kafka, never()).send(anyString(), anyString(), any());
    }

    @Test
    void upstreamFailureIsSwallowed() {
        when(budgetService.forMonth(any(), any())).thenThrow(new RuntimeException("user-service down"));

        detector.onExpenseLogged(event());

        verify(kafka, never()).send(anyString(), anyString(), any());
    }
}
