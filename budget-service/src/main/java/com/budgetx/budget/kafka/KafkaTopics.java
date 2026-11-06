package com.budgetx.budget.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopics {

    public static final String EXPENSE_LOGGED = "budget.expense.logged";
    public static final String OVERSPEND = "budget.overspend";

    @Bean
    public NewTopic overspendTopic() {
        return TopicBuilder.name(OVERSPEND).partitions(3).replicas(1).build();
    }
}
