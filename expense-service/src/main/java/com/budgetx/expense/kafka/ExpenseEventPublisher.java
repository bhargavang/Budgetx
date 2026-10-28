package com.budgetx.expense.kafka;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.budgetx.expense.dto.ExpenseEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Sends expense events to Kafka only after the database transaction commits, so consumers never
 * see an expense that was rolled back. A Kafka outage is logged and does not fail the request.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExpenseEventPublisher {

    private final KafkaTemplate<String, ExpenseEvent> kafkaTemplate;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(ExpenseEvent event) {
        kafkaTemplate.send(KafkaTopics.EXPENSE_LOGGED, event.userId().toString(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.warn("Failed to publish expense {} for user {}", event.expenseId(), event.userId(), ex);
                    }
                });
    }
}
