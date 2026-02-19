package com.example.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import static com.example.util.StringUtil.OPEN_AI;
import static com.example.util.StringUtil.TELEGRAM_BOT_API;

@Configuration
public class WebClientConfig {

    @Bean
    public WebClient llmWebClient() {
        return WebClient.builder()
                .baseUrl(OPEN_AI)
                .build();
    }

    @Bean
    public WebClient telegramBotClient() {
        return WebClient.builder()
                .baseUrl(TELEGRAM_BOT_API)
                .build();
    }
}
