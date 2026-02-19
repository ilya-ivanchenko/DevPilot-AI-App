package com.example.telegram;

import com.example.config.TelegramConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

import static com.example.util.StringUtil.CHAT_ID;
import static com.example.util.StringUtil.TELEGRAM_SEND_MESSAGE;
import static com.example.util.StringUtil.TEXT;

@Component
@RequiredArgsConstructor
@Slf4j
public class TelegramClient {

    private final WebClient telegramBotClient;
    private final TelegramConfig telegramConfig;

    public Mono<TelegramUpdateResponse> getUpdates(Long offset) {
        return telegramBotClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(telegramConfig.getToken() + "/getUpdates")
                        .queryParam("timeout", 30)
                        .queryParam("offset", offset)
                        .build())
                .retrieve()
                .bodyToMono(TelegramUpdateResponse.class)
                .doOnNext(resp -> log.debug("getUpdates: offset={}, ok={}, resultSize={}",
                        offset, resp.isOk(),
                        resp.getResult() != null ? resp.getResult().size() : 0));
    }

    public Mono<Void> sendMessage(Long chatId, String text) {
        return telegramBotClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path(TELEGRAM_SEND_MESSAGE)
                        .build(telegramConfig.getToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        CHAT_ID, chatId,
                        TEXT, text
                ))
                .retrieve()
                .bodyToMono(String.class)
                .doOnError(error -> log.warn("SendMessage failed: chatId={}, error={}", chatId,
                        error.getMessage()))
                .then();
    }

}
