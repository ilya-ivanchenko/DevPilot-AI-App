package com.example.llm;

import com.example.config.LlmConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.List;

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
