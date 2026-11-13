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

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String STATUS_OK = "ok";

    private final WebClient webClient;
    private final String endpoint;
    private final int maxTimeoutMs;
    @Getter
    private final boolean enabled;
    private final Object sessionLock = new Object();

    private String htmlSessionId;

    public FlareSolverrClient(
            @Value("${scraper.flaresolverr.url:http://localhost:8191/v1}") String endpoint,
            @Value("${scraper.flaresolverr.max-timeout-ms:20000}") int maxTimeoutMs,
            @Value("${scraper.flaresolverr.enabled:false}") boolean enabled) {
        this.endpoint = endpoint;
        this.maxTimeoutMs = maxTimeoutMs;
        this.enabled = enabled;
        this.webClient = WebClient.builder()
                .exchangeStrategies(ExchangeStrategies.builder()
                        .codecs(c -> c.defaultCodecs().maxInMemorySize(32 * 1024 * 1024))
                        .build())
                .build();
    }

    public String fetchJson(String url) { return fetchWithRetry(url, true); }
    public String fetchHtml(String url) { return fetchWithRetry(url, false); }

    // ---------- session handling ----------

    private String fetchWithRetry(String url, boolean json) {
        synchronized (sessionLock) {
            String sessionId = ensureSession();
            try {
                return fetch(url, json, sessionId);
            } catch (ResponseStatusException e) {
                if (!isMissingSession(e)) throw e;
                log.warn("FlareSolverr session {} is gone, recreating it", sessionId);
                htmlSessionId = null;
                return fetch(url, json, ensureSession());
            }
        }
    }

    private String ensureSession() {
        if (htmlSessionId != null) return htmlSessionId;

        String requestedName = "skroutz-html-" + UUID.randomUUID();
        FlareSolverrRequestDto createSessionRequest = FlareSolverrRequestDto.builder()
                .cmd("sessions.create")
                .session(requestedName)
                .build();
        FlareSolverrResponseDto response = execute(createSessionRequest);

        requireOk(response);
        htmlSessionId = response.getSession() != null ? response.getSession() : requestedName;
        log.info("Created FlareSolverr session {}", htmlSessionId);
        return htmlSessionId;
    }

    @PreDestroy
    public void destroySession() {
        if (htmlSessionId == null) return;
        try {
            FlareSolverrRequestDto destroySessionRequest = FlareSolverrRequestDto.builder()
                    .cmd("sessions.destroy")
                    .session(htmlSessionId)
                    .build();
            execute(destroySessionRequest);
            log.info("Destroyed FlareSolverr session {}", htmlSessionId);
        } catch (Exception e) {
            log.debug("Failed to destroy FlareSolverr session {}: {}", htmlSessionId, e.getMessage());
        } finally {
            htmlSessionId = null;
        }
    }

    // ---------- request execution ----------

    private String fetch(String url, boolean json, String sessionId) {
        log.info("Fetching via FlareSolverr: {}", url);

        FlareSolverrRequestDto request = FlareSolverrRequestDto.builder()
                .cmd("request.get")
                .url(url)
                .maxTimeout(maxTimeoutMs)
                .session(sessionId)
                .build();

        FlareSolverrResponseDto response = execute(request);

        if (response == null || !STATUS_OK.equals(response.getStatus())) {
            String msg = statusMessage(response);
            log.error("FlareSolverr failed for URL {}: {}", url, msg);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "FlareSolverr failed: " + msg);
        }

        FlareSolverrResponseDto.Solution solution = response.getSolution();
        int status = solution != null && solution.getStatus() != null ? solution.getStatus() : 200;
        if (HttpStatus.valueOf(status).isError()) {
            log.error("FlareSolverr returned status {} for URL {}", status, url);
            throw new ResponseStatusException(HttpStatus.valueOf(status),
                    "FlareSolverr fetch returned " + status);
        }

        String body = solution == null || solution.getResponse() == null ? "" : solution.getResponse();
        if (body.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "FlareSolverr returned an empty body");
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
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "FlareSolverr request failed: " + e.getMessage(), e);
        }
    }

    // ---------- helpers ----------

    private static void requireOk(FlareSolverrResponseDto response) {
        if (response == null || !STATUS_OK.equals(response.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Failed to create FlareSolverr session" + ": " + statusMessage(response));
        }
    }

    private static String statusMessage(FlareSolverrResponseDto response) {
        return response == null ? "empty response" : response.getMessage();
    }

    private boolean isMissingSession(ResponseStatusException e) {
        String msg = e.getReason();
        if (msg == null) return false;
        String n = msg.toLowerCase();
        return n.contains("session")
                && (n.contains("not found") || n.contains("does not exist") || n.contains("invalid"));
    }

    private String unwrapJson(String body) {
        /***
         * FlareSolverr sometimes wraps the JSON response in a <pre> tag, which is not valid JSON.
         * This method attempts to extract the JSON from the <pre> tag if present, otherwise returns the original body.
         *
         * <html>
         *   <head>
         *     <meta name="color-scheme" content="light dark">
         *     <meta charset="utf-8">
         *   </head>
         *   <body>
         *     <pre>
         *         {json_body}
         *     </pre>
         *     <div class="json-formatter-container">
         *     </div>
         *   </body>
         *  </html>
         */
        Element pre = Jsoup.parse(body).selectFirst("pre");
        if (pre == null) return body;
        String candidate = pre.wholeText();
        try {
            OBJECT_MAPPER.readTree(candidate);
            return candidate;
        } catch (Exception e) {
            return body;
        }
    }
}
