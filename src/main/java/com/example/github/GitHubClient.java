package com.example.github;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import static com.example.util.StringUtil.PR_PATTERN;
import static java.util.Objects.isNull;

/**
 * Fetches PR diff from public GitHub repositories. No token required (60 req/hour unauthenticated).
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class GitHubClient {

    private final WebClient gitHubWebClient;

    public Mono<String> fetchPrDiff(String prUrl) {
        if (Strings.isBlank(prUrl)) {
            return Mono.empty();
        }
        PrUrlParts parsed = parsePrUrl(prUrl);
        if (isNull(parsed)) {
            log.debug("Not a valid GitHub PR URL: {}", prUrl);
            return Mono.empty();
        }
        String path =
                "/repos/" + parsed.owner() + "/" + parsed.repo() + "/pulls/" + parsed.pullNumber();
        return gitHubWebClient.get()
                .uri(path)
                .accept(MediaType.parseMediaType("application/vnd.github.diff"))
                .retrieve()
                .bodyToMono(String.class)
                .doOnError(e -> log.warn("GitHub PR fetch failed: url={}, error={}", prUrl,
                        e.getMessage()));
    }

    public static boolean isGitHubPrUrl(String text) {
        if (Strings.isBlank(text)) {
            return false;
        }
        return PR_PATTERN.matcher(text.trim()).matches();
    }

    private static PrUrlParts parsePrUrl(String url) {
        if (Strings.isBlank(url)) {
            return null;
        }

        var matcher = PR_PATTERN.matcher(url.trim());
        if (!matcher.matches()) {
            return null;
        }
        return new PrUrlParts(matcher.group(1), matcher.group(2), matcher.group(3));
    }

    private record PrUrlParts(String owner, String repo, String pullNumber) {
    }
}
