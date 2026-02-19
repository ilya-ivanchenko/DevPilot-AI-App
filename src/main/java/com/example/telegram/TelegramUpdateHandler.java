package com.example.telegram;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static com.example.util.StringUtil.CMD_INTERVIEW;
import static com.example.util.StringUtil.CMD_REVIEW;
import static com.example.util.StringUtil.CMD_START;
import static com.example.util.StringUtil.HELP;
import static com.example.util.StringUtil.INTERVIEW_STUB;
import static com.example.util.StringUtil.REVIEW_STUB;
import static com.example.util.StringUtil.WELCOME;
import static java.util.Objects.isNull;

@Service
@RequiredArgsConstructor
@Slf4j
public class TelegramUpdateHandler {
    private final TelegramClient telegramClient;

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

                    String command = trimmed.split("\\s+", 2)[0];
                    String reply = switch (command) {
                        case CMD_START -> WELCOME;
                        case CMD_REVIEW -> REVIEW_STUB;
                        case CMD_INTERVIEW -> INTERVIEW_STUB;
                        default -> HELP;
                    };
                    return telegramClient.sendMessage(chatId, reply);
                })
                .then();
    }

    private void logIncomingMessage(Long chatId, String text) {
        String command = text.split("\\s+")[0];
        if (command.startsWith("/")) {
            log.info("User request: chatId={}, command={}", chatId, command);
        } else {
            log.info("User request: chatId={}, type=text, text={}", chatId, text);
        }
        log.debug("User request: chatId={}, text={}", chatId, text);
    }
}
