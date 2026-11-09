package org.skroutz.scraper.skroutzwebscraper.scraping.infrastructure.scraper;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.skroutz.scraper.skroutzwebscraper.scraping.infrastructure.dto.api.FlareSolverrRequestDto;
import org.skroutz.scraper.skroutzwebscraper.scraping.infrastructure.dto.api.FlareSolverrResponseDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.UUID;

@Slf4j
@Component
public class FlareSolverrClient {

    private final WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String endpoint;
    private final int maxTimeoutMs;
    @Getter
    private final boolean enabled;
    private final Object sessionLock = new Object();

    private String htmlSessionId;

    public FlareSolverrClient(@Value("${scraper.flaresolverr.url:http://localhost:8191/v1}") String endpoint,
                              @Value("${scraper.flaresolverr.max-timeout-ms:60000}") int maxTimeoutMs,
                              @Value("${scraper.flaresolverr.enabled:false}") boolean enabled) {
        this.endpoint = endpoint;
        this.maxTimeoutMs = maxTimeoutMs;
        this.enabled = enabled;
        this.webClient = WebClient.builder()
                .exchangeStrategies(ExchangeStrategies.builder()
                        .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(32 * 1024 * 1024))
                        .build())
                .build();
    }

    public String fetchJson(String url) {
        return fetch(url, true, null);
    }

    public String fetchHtml(String url) {
        synchronized (sessionLock) {
            String sessionId = ensureSession();
            try {
                return fetch(url, false, sessionId);
            } catch (ResponseStatusException e) {
                if (!isMissingSession(e)) {
                    throw e;
                }
                log.warn("FlareSolverr session {} is gone, recreating it", sessionId);
                htmlSessionId = null;
                return fetch(url, false, ensureSession());
            }
        }
    }

    private String ensureSession() {
        if (htmlSessionId != null) {
            return htmlSessionId;
        }

        String requestedName = "skroutz-html-" + UUID.randomUUID();
        FlareSolverrResponseDto response = execute(FlareSolverrRequestDto.builder()
                .cmd("sessions.create")
                .session(requestedName)
                .build());

        if (response == null || !"ok".equals(response.getStatus())) {
            String message = response == null ? "empty response" : response.getMessage();
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Failed to create FlareSolverr session: " + message
            );
        }

        htmlSessionId = response.getSession() != null ? response.getSession() : requestedName;
        log.info("Created FlareSolverr session {}", htmlSessionId);
        return htmlSessionId;
    }

    private String fetch(String url, boolean json, String sessionId) {
        log.info("Fetching via FlareSolverr: {}", url);

        FlareSolverrRequestDto request = FlareSolverrRequestDto.builder()
                .cmd("request.get")
                .url(url)
                .maxTimeout(maxTimeoutMs)
                .session(sessionId)
                .build();

        FlareSolverrResponseDto response = execute(request);

        if (response == null || !"ok".equals(response.getStatus())) {
            String message = response == null ? "empty response" : response.getMessage();
            log.error("FlareSolverr failed for URL {}: {}", url, message);
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "FlareSolverr failed: " + message
            );
        }

        FlareSolverrResponseDto.Solution solution = response.getSolution();
        int status = solution != null && solution.getStatus() != null ? solution.getStatus() : 200;
        if (status >= 400) {
            log.error("FlareSolverr returned status {} for URL {}", status, url);
            throw new ResponseStatusException(
                    HttpStatus.valueOf(status),
                    "FlareSolverr fetch returned " + status
            );
        }

        String body = solution == null || solution.getResponse() == null ? "" : solution.getResponse();
        if (body.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "FlareSolverr returned an empty body"
            );
        }

        return json ? unwrapJson(body) : body;
    }

    private FlareSolverrResponseDto execute(FlareSolverrRequestDto request) {
        try {
            return webClient.post()
                    .uri(endpoint)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(FlareSolverrResponseDto.class)
                    .block(Duration.ofMillis(maxTimeoutMs + 15_000L));
        } catch (Exception e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "FlareSolverr request failed: " + e.getMessage(),
                    e
            );
        }
    }

    private boolean isMissingSession(ResponseStatusException e) {
        String message = e.getReason();
        if (message == null) {
            return false;
        }
        String normalized = message.toLowerCase();
        return normalized.contains("session")
                && (normalized.contains("not found")
                || normalized.contains("does not exist")
                || normalized.contains("invalid"));
    }

    private String unwrapJson(String body) {
        Element pre = Jsoup.parse(body).selectFirst("pre");
        if (pre == null) {
            return body;
        }

        String candidate = pre.wholeText();
        try {
            objectMapper.readTree(candidate);
            return candidate;
        } catch (Exception e) {
            return body;
        }
    }

    @PreDestroy
    public void destroySession() {
        if (htmlSessionId == null) {
            return;
        }
        try {
            execute(FlareSolverrRequestDto.builder()
                    .cmd("sessions.destroy")
                    .session(htmlSessionId)
                    .build());
            log.info("Destroyed FlareSolverr session {}", htmlSessionId);
        } catch (Exception e) {
            log.debug("Failed to destroy FlareSolverr session {}: {}", htmlSessionId, e.getMessage());
        } finally {
            htmlSessionId = null;
        }
    }
}
