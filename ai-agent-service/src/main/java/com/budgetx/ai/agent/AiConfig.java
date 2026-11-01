package com.budgetx.ai.agent;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.anthropic.client.okhttp.AnthropicOkHttpClient;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
public class AiConfig {

    @Bean
    public AiPlanner aiPlanner(@Value("${budgetx.ai.anthropic-api-key:}") String apiKey,
                               @Value("${budgetx.ai.model:claude-opus-5-5}") String model) {
        if (apiKey == null || apiKey.isBlank()) {
            log.info("ANTHROPIC_API_KEY not set: guidance runs rules-only");
            return new DisabledAiPlanner();
        }
        log.info("AI guidance enabled with model {}", model);
        return new AnthropicAiPlanner(AnthropicOkHttpClient.builder()
                .apiKey(apiKey)
                .timeout(Duration.ofSeconds(90))
                .maxRetries(1)
                .build(), model);
    }
}
