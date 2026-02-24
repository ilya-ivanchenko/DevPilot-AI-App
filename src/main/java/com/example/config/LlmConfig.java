package com.example.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@ConfigurationProperties(prefix = "llm")
@Getter
@Setter
public class LlmConfig {
    private String apiKey;
    private String baseUrl;
    private String model;
    private Duration timeout;
    private Prompts prompts = new Prompts();


    @Getter
    @Setter
    public static class Prompts {
        private String reviewSystem;
    }
}
