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
    private Review review = new Review();
    private Interview interview = new Interview();


    @Getter
    @Setter
    public static class Review {
        private String empty;
        private String publicOnly;
        private String error;
        private String timeout;
    }


    @Getter
    @Setter
    public static class Interview {
        private String empty;
        private String error;
        private String timeout;
        private String stop;
        private String intro;
    }
}
