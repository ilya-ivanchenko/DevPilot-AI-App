package com.example.llm;

import com.example.config.LlmConfig;
import com.example.github.GitHubClient;
import com.example.llm.tool.ReviewTools;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.example.util.StringUtil.CHAT_COMPLETIONS_PATH;
import static com.example.util.StringUtil.MAX_TOKENS;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

@Component
@RequiredArgsConstructor
@Slf4j
public class LlmClient {
    private final WebClient llmWebClient;
    private final LlmConfig llmConfig;
    private final GitHubClient githubClient;
    private final ObjectMapper objectMapper;

    /**
     * Review with tool-calling: LLM can call getPullRequestDiff to fetch PR diff, then we return its final reply.
     * Use this when the payload is a PR URL.
     */
    public Mono<String> reviewWithPrTool(String prUrl) {
        String systemPrompt = llmConfig.getPrompts().getReviewSystem();
        String userContent =
                "Review the following pull request. Use the tool to get the diff first: " + prUrl;
        List<OpenAiRequest.Message> messages = List.of(
                OpenAiRequest.Message.builder().role("system").content(systemPrompt).build(),
                OpenAiRequest.Message.builder().role("user").content(userContent).build()
        );
        return postWithTools(messages, ReviewTools.reviewTools())
                .flatMap(resp -> handleToolCallResponse(messages, resp));
    }

    public Mono<String> review(String userContent) {
        String systemPrompt = llmConfig.getPrompts().getReviewSystem();
        List<OpenAiRequest.Message> messages = List.of(
                OpenAiRequest.Message.builder().role("system").content(systemPrompt).build(),
                OpenAiRequest.Message.builder().role("user").content(userContent).build()
        );
        return executeChatCompletion(messages);
    }

    public Mono<String> startInterview(String topic) {
        String systemPrompt = llmConfig.getPrompts().getInterviewSystem();
        String userPrompt = "Start a technical interview on topic: \"" + topic
                + "\". Ask only the first question.";
        List<OpenAiRequest.Message> messages = List.of(
                OpenAiRequest.Message.builder().role("system").content(systemPrompt).build(),
                OpenAiRequest.Message.builder().role("user").content(userPrompt).build()
        );
        return executeChatCompletion(messages);
    }

    public Mono<String> continueInterview(String topic, String userAnswer) {
        String systemPrompt = llmConfig.getPrompts().getInterviewSystem();
        String userPrompt = "User answer in interview on topic \"" + topic + "\":\n"
                + userAnswer
                + "\n\nBased on this answer, ask the next question. Do not repeat previous questions or answers.";
        List<OpenAiRequest.Message> messages = List.of(
                OpenAiRequest.Message.builder().role("system").content(systemPrompt).build(),
                OpenAiRequest.Message.builder().role("user").content(userPrompt).build()
        );
        return executeChatCompletion(messages);
    }

    public Mono<String> finishInterview(String topic, List<String> answers) {
        String systemPrompt = llmConfig.getPrompts().getInterviewSystem();
        StringBuilder sb = new StringBuilder();
        String headerTemplate = llmConfig.getPrompts().getInterviewFinishHeader();
        sb.append(String.format(headerTemplate, topic)).append("\n\n");
        for (int i = 0; i < answers.size(); i++) {
            sb.append(i + 1)
                    .append(") ")
                    .append(answers.get(i))
                    .append("\n\n");
        }
        String instruction = llmConfig.getPrompts().getInterviewFinishInstruction();
        sb.append(instruction);

        List<OpenAiRequest.Message> messages = List.of(
                OpenAiRequest.Message.builder().role("system").content(systemPrompt).build(),
                OpenAiRequest.Message.builder().role("user").content(sb.toString()).build()
        );
        return executeChatCompletion(messages);
    }

    private Mono<OpenAiResponse> postWithTools(List<OpenAiRequest.Message> messages,
            List<OpenAiRequest.Tool> tools) {
        var request = OpenAiRequest.builder()
                .model(llmConfig.getModel())
                .messages(messages)
                .maxTokens(MAX_TOKENS)
                .tools(tools)
                .build();

        return llmWebClient.post()
                .uri(CHAT_COMPLETIONS_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(OpenAiResponse.class)
                .timeout(llmConfig.getTimeout())
                .retryWhen(Retry.backoff(2, Duration.ofSeconds(2))
                        .filter(this::isTransientConnectionError)
                        .doBeforeRetry(
                                s -> log.warn("LLM request retry after transient error, attempt {}",
                                        s.totalRetries() + 1)))
                .doOnError(e -> log.warn("LLM request failed: {}", e.getMessage()));
    }

    private Mono<String> handleToolCallResponse(List<OpenAiRequest.Message> messages,
            OpenAiResponse resp) {
        OpenAiResponse.Message msg = nonNull(resp.getChoices()) && !resp.getChoices().isEmpty()
                ? resp.getChoices().getFirst().getMessage()
                : null;
        if (isNull(msg) || isNull(msg.getToolCalls()) || msg.getToolCalls().isEmpty()) {
            return Mono.just(extractContent(resp));
        }

        return Flux.fromIterable(msg.getToolCalls())
                .flatMap(this::executeToolCallMono)
                .collectList()
                .flatMap(toolResults -> {
                    List<OpenAiRequest.Message> withTools = new ArrayList<>(messages);
                    withTools.add(toAssistantMessage(msg));
                    for (int i = 0; i < msg.getToolCalls().size(); i++) {
                        withTools.add(OpenAiRequest.Message.builder()
                                .role("tool")
                                .toolCallId(msg.getToolCalls().get(i).getId())
                                .content(toolResults.get(i))
                                .build());
                    }
                    return executeChatCompletion(withTools);
                });
    }

    private OpenAiRequest.Message toAssistantMessage(OpenAiResponse.Message msg) {
        List<OpenAiRequest.ToolCallSpec> specs = null;
        if (msg.getToolCalls() != null && !msg.getToolCalls().isEmpty()) {
            specs = msg.getToolCalls().stream()
                    .map(tc -> OpenAiRequest.ToolCallSpec.builder()
                            .id(tc.getId())
                            .type(tc.getType() != null ? tc.getType() : "function")
                            .function(OpenAiRequest.ToolCallSpec.FunctionCallRef.builder()
                                    .name(tc.getFunction() != null ?
                                            tc.getFunction().getName() :
                                            null)
                                    .arguments(tc.getFunction() != null ?
                                            tc.getFunction().getArguments() :
                                            null)
                                    .build())
                            .build())
                    .toList();
        }
        return OpenAiRequest.Message.builder()
                .role("assistant")
                .content(msg.getContent())
                .toolCalls(specs)
                .build();
    }

    private Mono<String> executeToolCallMono(OpenAiResponse.ToolCall tc) {
        if (isNull(tc.getFunction()) || !ReviewTools.GET_PULL_REQUEST_DIFF.equals(
                tc.getFunction().getName())) {
            return Mono.just("Tool not supported or missing function.");
        }
        String args = tc.getFunction().getArguments();
        if (args.isBlank()) {
            return Mono.just("Missing arguments.");
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = objectMapper.readValue(args, Map.class);
            Object prUrl = map.get("pr_url");
            if (isNull(prUrl)) {
                return Mono.just("Missing pr_url in arguments.");
            }
            return githubClient.fetchPrDiff(prUrl.toString())
                    .defaultIfEmpty("(Empty or failed to fetch diff)")
                    .onErrorReturn("(Error fetching PR diff: " + prUrl + ")");
        } catch (Exception e) {
            log.warn("Failed to parse tool arguments: {}", e.getMessage());
            return Mono.just("(Invalid arguments: " + e.getMessage() + ")");
        }
    }

    private Mono<String> executeChatCompletion(List<OpenAiRequest.Message> messages) {
        var request = OpenAiRequest.builder()
                .model(llmConfig.getModel())
                .messages(messages)
                .maxTokens(MAX_TOKENS)
                .build();

        return llmWebClient.post()
                .uri(CHAT_COMPLETIONS_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(OpenAiResponse.class)
                .timeout(llmConfig.getTimeout())
                .map(this::extractContent)
                .retryWhen(Retry.backoff(2, Duration.ofSeconds(2))
                        .filter(this::isTransientConnectionError)
                        .doBeforeRetry(
                                s -> log.warn("LLM request retry after transient error, attempt {}",
                                        s.totalRetries() + 1)))
                .doOnError(e -> log.warn("LLM request failed: {}", e.getMessage()));
    }

    private boolean isTransientConnectionError(Throwable t) {
        if (isNull(t))
            return false;
        String msg = t.getMessage();
        if (nonNull(msg) && (msg.contains("prematurely closed") || msg.contains(
                "Connection reset") || msg.contains("connection closed"))) {
            return true;
        }
        if (nonNull(t.getCause()))
            return isTransientConnectionError(t.getCause());
        return false;
    }

    private String extractContent(OpenAiResponse resp) {
        if (isNull(resp) || isNull(resp.getChoices()) || resp.getChoices().isEmpty()) {
            return "";
        }
        OpenAiResponse.Choice first = resp.getChoices().getFirst();
        if (isNull(first) || isNull(first.getMessage())) {
            return "";
        }
        String content = first.getMessage().getContent();
        return nonNull(content) ? content : "";
    }
}
