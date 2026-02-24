package com.example.llm;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class OpenAiRequest {
    String model;
    List<Message> messages;
    @JsonProperty("max_tokens")
    Integer maxTokens;


    @Value
    @Builder
    public static class Message {
        String role;
        String content;
    }
}
