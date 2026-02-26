package com.example.llm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class OpenAiResponse {
    private List<Choice> choices;


    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Choice {
        private Message message;
    }


    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Message {
        private String content;
        /**
         * Optional tool calls returned by the model.
         */
        @JsonProperty("tool_calls")
        private List<ToolCall> toolCalls;
    }


    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ToolCall {
        private String id;
        private String type;
        private FunctionCall function;


        @Data
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class FunctionCall {
            private String name;
            private String arguments;
        }
    }
}
