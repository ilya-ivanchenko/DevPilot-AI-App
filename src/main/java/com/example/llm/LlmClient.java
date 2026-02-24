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
        var request = OpenAiRequest.builder()
                .model(llmConfig.getModel())
                .messages(List.of(
                        OpenAiRequest.Message.builder().role("system").content(systemPrompt)
                                .build(),
                        OpenAiRequest.Message.builder().role("user").content(userContent).build()
                ))
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
