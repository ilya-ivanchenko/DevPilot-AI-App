package com.example.llm;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Map;

@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OpenAiRequest {
    String model;
    List<Message> messages;
    @JsonProperty("max_tokens")
    Integer maxTokens;
    /**
     * Optional tools for tool calling (OpenAI tools API).
     */
    List<Tool> tools;


    @Value
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Message {
        String role;
        String content;
        /**
         * For assistant message: tool calls requested by the model.
         */
        @JsonProperty("tool_calls")
        List<ToolCallSpec> toolCalls;
        /**
         * For tool message: id of the tool call this result belongs to.
         */
        @JsonProperty("tool_call_id")
        String toolCallId;
    }


    /**
     * Assistant message tool call (mirrors API).
     */
    @Value
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ToolCallSpec {
        String id;
        String type;
        FunctionCallRef function;


        @Value
        @Builder
        public static class FunctionCallRef {
            String name;
            String arguments;
        }
    }


    /**
     * Tool definition for OpenAI tool-calling.
     */
    @Value
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Tool {
        String type;
        Function function;


        @Value
        @Builder
        @JsonInclude(JsonInclude.Include.NON_NULL)
        public static class Function {
            String name;
            String description;
            /** JSON schema for arguments. */
            Map<String, Object> parameters;
        }
    }
}
