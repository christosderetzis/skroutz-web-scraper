package org.skroutz.scraper.skroutzwebscraper.scraper;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

@Slf4j
public abstract class BaseScraper {

    protected final WebClient webClient;
    protected final long timeoutMillis;
    protected final long retryDelayMillis;
    protected final int maxRetries;

    protected BaseScraper(WebClient webClient, long timeoutMillis, int retryDelayMillis, int maxRetries) {
        this.webClient = webClient;
        this.timeoutMillis = timeoutMillis;
        this.retryDelayMillis = retryDelayMillis;
        this.maxRetries = maxRetries;
    }

    protected <T> T fetch(String url, Class<T> responseType, String context) {
        log.info("Fetching {} data from URL: {}", context, url);

        try {
            T response = webClient.get()
                    .uri(url)
                    .retrieve()
                    .onStatus(
                            status -> status.is4xxClientError() || status.is5xxServerError(),
                            clientResponse -> {
                                log.error("Error fetching {}. Status: {}", context, clientResponse.statusCode());
                                return Mono.error(new ResponseStatusException(
                                        clientResponse.statusCode(),
                                        "Failed to fetch " + context
                                ));
                            }
                    )
                    .bodyToMono(responseType)
                    .timeout(Duration.ofMillis(timeoutMillis))
                    .retryWhen(Retry.backoff(maxRetries, Duration.ofMillis(retryDelayMillis))
                            .maxBackoff(Duration.ofSeconds(10))
                            .filter(this::shouldRetry)
                            .doBeforeRetry(retrySignal -> {
                                String reason = getRetryReason(retrySignal.failure());
                                log.warn("{} Retrying attempt {}/{} after backoff...",
                                        reason,
                                        retrySignal.totalRetries() + 1,
                                        maxRetries);
                            })
                            .onRetryExhaustedThrow((spec, signal) -> {
                                String errorDetails = getErrorDetails(signal.failure());
                                log.error("Max retries ({}) exhausted for URL: {}. {}",
                                        maxRetries, url, errorDetails);
                                return signal.failure();
                            })
                    )
                    .block();

            if (response == null) {
                throw new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Null response for " + context
                );
            }

            log.info("Successfully fetched {}", context);
            return response;

        } catch (Exception e) {
            log.error("Error fetching {} from URL: {}", context, url, e);
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to fetch " + context + ": " + e.getMessage(),
                    e
            );
        }
    }

    private boolean shouldRetry(Throwable throwable) {
        if (throwable instanceof ResponseStatusException rse) {
            return isRetryableStatus(rse.getStatusCode().value());
        }
        if (throwable instanceof WebClientResponseException wcre) {
            return isRetryableStatus(wcre.getStatusCode().value());
        }
        if (throwable instanceof TimeoutException) {
            return true;
        }
        return false;
    }

    private boolean isRetryableStatus(int statusCode) {
        return statusCode == 403 || // FORBIDDEN
               statusCode == 408 || // REQUEST_TIMEOUT
               statusCode == 429 || // TOO_MANY_REQUESTS
               (statusCode >= 500 && statusCode < 600); // 5xx SERVER_ERROR
    }

    private String getRetryReason(Throwable throwable) {
        if (throwable instanceof ResponseStatusException rse) {
            return "Received " + rse.getStatusCode() + ".";
        }
        if (throwable instanceof WebClientResponseException wcre) {
            return "Received " + wcre.getStatusCode() + ".";
        }
        if (throwable instanceof TimeoutException) {
            return "Request timeout.";
        }
        return "Error occurred.";
    }

    private String getErrorDetails(Throwable throwable) {
        if (throwable instanceof ResponseStatusException rse) {
            return "HTTP Status: " + rse.getStatusCode();
        }
        if (throwable instanceof WebClientResponseException wcre) {
            return "HTTP Status: " + wcre.getStatusCode();
        }
        if (throwable instanceof TimeoutException) {
            return "Timeout after " + timeoutMillis + " ms";
        }
        return "Error: " + throwable.getMessage();
    }
}
