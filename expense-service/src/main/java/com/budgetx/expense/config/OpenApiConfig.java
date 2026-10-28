package com.budgetx.expense.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI expenseServiceOpenApi() {
        return new OpenAPI().info(new Info().title("BudgetX Expense Service").version("1.0.0")
                .description("Expense tracking and summaries. Call through the API gateway, which sets X-User-Id."));
    }
}
