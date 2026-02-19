package com.example.telegram;

import com.example.config.TelegramConfig;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

import static java.util.Objects.nonNull;

@Service
@RequiredArgsConstructor
@Slf4j
public class TelegramPollingService {

    private final TelegramClient telegramClient;
    private final TelegramUpdateHandler telegramUpdateHandler;
    private final TelegramConfig telegramConfig;

    private final AtomicLong offset = new AtomicLong(0);

    @PostConstruct
    public void startPolling() {
        pollOnce()
                .then(Mono.delay(telegramConfig.getPollingInterval()))
                .repeat()
                .subscribe();
    }

    private Mono<Void> pollOnce() {
        return Mono.defer(() -> telegramClient.getUpdates(offset.get()))
                .flatMap(response -> {
                    if (response.isOk() && nonNull(response.getResult()) && !response.getResult()
                            .isEmpty()) {
                        var nextOffset = response.getResult().stream()
                                .map(TelegramUpdateResponse.Update::getUpdateId)
                                .filter(Objects::nonNull)
                                .max(Long::compareTo)
                                .map(id -> id + 1)
                                .orElse(offset.get());
                        offset.set(nextOffset);
                    }
                    return telegramUpdateHandler.handleRawUpdate(response);
                })
                .onErrorResume(ex -> {
                    log.error("Polling getUpdates failed: {}", ex.getMessage(), ex);
                    return Mono.empty();
                });
    }

}
