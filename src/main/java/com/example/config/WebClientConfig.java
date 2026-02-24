package com.example.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;

import static com.example.util.StringUtil.GITHUB_API_BASE;
import static com.example.util.StringUtil.TELEGRAM_BOT_API;

@Configuration
public class WebClientConfig {

    @Bean
    public WebClient llmWebClient(LlmConfig llmConfig) {
        return WebClient.builder()
                .baseUrl(llmConfig.getBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + llmConfig.getApiKey())
                .build();
    }

    @Bean
    public WebClient telegramBotClient() {
        return WebClient.builder()
                .baseUrl(TELEGRAM_BOT_API)
                .build();
    }

    @Bean
    public WebClient gitHubWebClient() {
        return WebClient.builder()
                .baseUrl(GITHUB_API_BASE)
                .build();
    }
}
