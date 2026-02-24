package com.example.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.messages")
@Getter
@Setter
public class AppMessagesConfig {
    private String reviewEmpty;
    private String reviewPublicOnly;
    private String reviewError;
    private String reviewTimeout;
}
