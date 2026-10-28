package com.budgetx.expense.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopics {

    public static final String EXPENSE_LOGGED = "budget.expense.logged";

    @Bean
    public NewTopic expenseLoggedTopic() {
        return TopicBuilder.name(EXPENSE_LOGGED).partitions(3).replicas(1).build();
    }
}
