package org.skroutz.scraper.skroutzwebscraper.scraping.scraper;

import lombok.extern.slf4j.Slf4j;
import org.skroutz.scraper.skroutzwebscraper.scraping.dto.PriceHistoryResponseDto;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Component
@Slf4j
public class PriceHistoryScraper {

    private final WebClient webClient;
    private final int timeoutSeconds;

    public PriceHistoryScraper(WebClient webClient,
                               @org.springframework.beans.factory.annotation.Value("${scraper.timeout-seconds:30}") int timeoutSeconds) {
        this.webClient = webClient;
        this.timeoutSeconds = timeoutSeconds;
    }

    public PriceHistoryResponseDto fetchPriceHistory(String url) {
        log.info("Fetching price history data from URL: {}", url);

        try {
            PriceHistoryResponseDto response = webClient.get()
                    .uri(url)
                    .retrieve()
                    .onStatus(
                            status -> status.is4xxClientError() || status.is5xxServerError(),
                            clientResponse -> {
                                log.error("Error fetching price history. Status: {}", clientResponse.statusCode());
                                return Mono.error(new ResponseStatusException(
                                        clientResponse.statusCode(),
                                        "Failed to fetch price history from API"
                                ));
                            }
                    )
                    .bodyToMono(PriceHistoryResponseDto.class)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .block();

            if (response == null) {
                log.error("Received null response from price history API");
                throw new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Failed to fetch price history: null response"
                );
            }

            log.info("Successfully fetched price history data");
            return response;

        } catch (Exception e) {
            log.error("Error fetching price history from URL: {}", url, e);
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to fetch price history: " + e.getMessage(),
                    e
            );
        }
    }
}
