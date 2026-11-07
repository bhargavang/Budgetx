package com.budgetx.ai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI aiAgentServiceOpenApi() {
        return new OpenAPI().info(new Info().title("BudgetX AI Guidance Service").version("1.0.0")
                .description("Free educational money guidance: hard safety rules first, then an optional "
                        + "AI-personalised split that is validated against those rules."));
    }
}
