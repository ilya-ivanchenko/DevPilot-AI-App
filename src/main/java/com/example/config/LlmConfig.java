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
    /**
     * Max number of interview questions before final feedback.
     */
    private int interviewMaxQuestions;
    private Prompts prompts = new Prompts();


    @Getter
    @Setter
    public static class Prompts {
        private String reviewSystem;
        private String interviewSystem;
        /**
         * Header text for interview feedback prompt (may contain %s for topic).
         */
        private String interviewFinishHeader;
        /**
         * Instruction text for interview feedback prompt.
         */
        private String interviewFinishInstruction;
    }
}
