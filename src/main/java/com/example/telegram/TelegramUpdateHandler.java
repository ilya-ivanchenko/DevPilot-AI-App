package com.example.telegram;

import com.example.config.AppMessagesConfig;
import com.example.config.LlmConfig;
import com.example.llm.LlmClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;

import static com.example.github.GitHubClient.isGitHubPrUrl;
import static com.example.util.StringUtil.CMD_INTERVIEW;
import static com.example.util.StringUtil.CMD_REVIEW;
import static com.example.util.StringUtil.CMD_START;
import static com.example.util.StringUtil.CMD_STOP;
import static com.example.util.StringUtil.HELP;
import static com.example.util.StringUtil.WELCOME;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

@Service
@RequiredArgsConstructor
@Slf4j
public class TelegramUpdateHandler {
    private final TelegramClient telegramClient;
    private final LlmClient llmClient;
    private final AppMessagesConfig appMessages;
    private final LlmConfig llmConfig;

    /**
     * Chat IDs waiting for the next message as /review payload (code or PR link).
     */
    private final Set<Long> waitingForReviewPayload = ConcurrentHashMap.newKeySet();

    /**
     * Active interview topics by chat ID.
     */
    private final Map<Long, String> interviewTopics = new ConcurrentHashMap<>();
    /**
     * Collected interview answers by chat ID.
     */
    private final Map<Long, List<String>> interviewAnswers = new ConcurrentHashMap<>();

    public Mono<Void> handleRawUpdate(TelegramUpdateResponse response) {
        if (!response.isOk() || response.getResult().isEmpty()) {
            return Mono.empty();
        }

        return Flux.fromIterable(response.getResult())
                .flatMap(update -> {
                    if (isNull(update.getUpdateId()) || isNull(update.getMessage())) {
                        return Mono.empty();
                    }
                    var chatId = update.getMessage().getChat().getId();
                    var text = update.getMessage().getText();

                    if (Strings.isBlank(text)) {
                        return Mono.empty();
                    }

                    var trimmed = text.trim();
                    logIncomingMessage(chatId, trimmed);

                    if (waitingForReviewPayload.remove(chatId) && !trimmed.startsWith("/")) {
                        return handleReview(chatId, trimmed);
                    }

                    if (interviewTopics.containsKey(chatId) && !trimmed.startsWith("/")) {
                        return handleInterviewAnswer(chatId, trimmed);
                    }

                    String command = trimmed.split("\\s+", 2)[0];
                    switch (command) {
                        case CMD_START -> {
                            interviewTopics.remove(chatId);
                            interviewAnswers.remove(chatId);
                            String welcome =
                                    WELCOME + "\n" + appMessages.getReview().getPublicOnly();
                            return telegramClient.sendMessage(chatId, welcome);
                        }
                        case CMD_STOP -> {
                            boolean hadInterview =
                                    interviewTopics.containsKey(
                                            chatId) || interviewAnswers.containsKey(
                                            chatId);
                            interviewTopics.remove(chatId);
                            interviewAnswers.remove(chatId);
                            if (hadInterview) {
                                return telegramClient.sendMessage(chatId,
                                        appMessages.getInterview().getStop());
                            }
                            return telegramClient.sendMessage(chatId, HELP);
                        }
                        case CMD_REVIEW -> {
                            interviewTopics.remove(chatId);
                            interviewAnswers.remove(chatId);
                            String payload = trimmed.length() > CMD_REVIEW.length()
                                    ? trimmed.substring(CMD_REVIEW.length()).trim()
                                    : "";
                            if (Strings.isBlank(payload)) {
                                waitingForReviewPayload.add(chatId);
                                return telegramClient.sendMessage(chatId,
                                        appMessages.getReview().getEmpty());
                            }
                            return handleReview(chatId, payload);
                        }
                        case CMD_INTERVIEW -> {
                            String payload = trimmed.length() > CMD_INTERVIEW.length()
                                    ? trimmed.substring(CMD_INTERVIEW.length()).trim()
                                    : "";
                            if (Strings.isBlank(payload)) {
                                return telegramClient.sendMessage(chatId,
                                        appMessages.getInterview().getEmpty());
                            }
                            interviewTopics.put(chatId, payload);
                            interviewAnswers.put(chatId, new java.util.ArrayList<>());
                            int maxQuestions = llmConfig.getInterviewMaxQuestions();
                            String introTemplate = appMessages.getInterview().getIntro();
                            String intro = String.format(introTemplate, maxQuestions);
                            return telegramClient.sendMessage(chatId, intro)
                                    .then(handleInterview(chatId, payload));
                        }
                        default -> {
                            return telegramClient.sendMessage(chatId, HELP);
                        }
                    }
                })
                .then();
    }

    private Mono<Void> handleReview(Long chatId, String payload) {
        //add private repos?
        if (Strings.isBlank(payload)) {
            return telegramClient.sendMessage(chatId, appMessages.getReview().getEmpty());
        }

        Mono<String> reviewMono;
        if (isGitHubPrUrl(payload)) {
            reviewMono = llmClient.reviewWithPrTool(payload);
        } else {
            reviewMono = llmClient.review(payload);
        }

        return reviewMono
                .flatMap(reviewText -> telegramClient.sendMessage(chatId, reviewText)
                        .then(telegramClient.sendMessage(chatId, HELP)))
                .onErrorResume(e -> {
                    Throwable cause = nonNull(e.getCause()) ? e.getCause() : e;
                    if (cause instanceof TimeoutException) {
                        return telegramClient.sendMessage(chatId,
                                        appMessages.getReview().getTimeout())
                                .then(telegramClient.sendMessage(chatId, HELP));
                    }
                    return telegramClient.sendMessage(chatId,
                                    appMessages.getReview().getError())
                            .then(telegramClient.sendMessage(chatId, HELP));
                });
    }

    private Mono<Void> handleInterview(Long chatId, String topic) {
        if (Strings.isBlank(topic)) {
            return telegramClient.sendMessage(chatId, appMessages.getInterview().getEmpty());
        }

        return llmClient.startInterview(topic)
                .flatMap(question -> telegramClient.sendMessage(chatId, question))
                .onErrorResume(e -> {
                    Throwable cause = nonNull(e.getCause()) ? e.getCause() : e;
                    if (cause instanceof TimeoutException) {
                        return telegramClient.sendMessage(chatId,
                                appMessages.getInterview().getTimeout());
                    }
                    return telegramClient.sendMessage(chatId,
                            appMessages.getInterview().getError());
                });
    }

    private Mono<Void> handleInterviewAnswer(Long chatId, String answer) {
        String topic = interviewTopics.get(chatId);
        if (Strings.isBlank(answer) || Strings.isBlank(topic)) {
            return Mono.empty();
        }

        List<String> answers = interviewAnswers.computeIfAbsent(chatId,
                id -> new ArrayList<>());
        answers.add(answer);

        int maxQuestions = llmConfig.getInterviewMaxQuestions();
        if (answers.size() >= maxQuestions) {
            interviewTopics.remove(chatId);
            interviewAnswers.remove(chatId);

            return llmClient.finishInterview(topic, answers)
                    .flatMap(feedback -> telegramClient.sendMessage(chatId, feedback)
                            .then(telegramClient.sendMessage(chatId, HELP)))
                    .onErrorResume(e -> {
                        Throwable cause = nonNull(e.getCause()) ? e.getCause() : e;
                        if (cause instanceof TimeoutException) {
                            return telegramClient.sendMessage(chatId,
                                            appMessages.getInterview().getTimeout())
                                    .then(telegramClient.sendMessage(chatId, HELP));
                        }
                        return telegramClient.sendMessage(chatId,
                                        appMessages.getInterview().getError())
                                .then(telegramClient.sendMessage(chatId, HELP));
                    });
        }

        return llmClient.continueInterview(topic, answer)
                .flatMap(question -> telegramClient.sendMessage(chatId, question))
                .onErrorResume(e -> {
                    Throwable cause = nonNull(e.getCause()) ? e.getCause() : e;
                    if (cause instanceof TimeoutException) {
                        return telegramClient.sendMessage(chatId,
                                appMessages.getInterview().getTimeout());
                    }
                    return telegramClient.sendMessage(chatId,
                            appMessages.getInterview().getError());
                });
    }

    private void logIncomingMessage(Long chatId, String text) {
        String command = text.split("\\s+")[0];
        if (command.startsWith("/")) {
            log.info("User request: chatId={}, command={}", chatId, command);
        } else {
            log.info("User request: chatId={}, type=text, length={}", chatId, text.length());
        }
        log.debug("User request: chatId={}, text={}", chatId, text);
    }
}
