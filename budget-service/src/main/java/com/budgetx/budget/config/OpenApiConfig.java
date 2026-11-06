package com.budgetx.budget.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI budgetServiceOpenApi() {
        return new OpenAPI().info(new Info().title("BudgetX Budget Service").version("1.0.0")
                .description("Monthly budget: salary, EMIs, spending, disposable and investable amounts."));
    }
}
