package org.skroutz.scraper.skroutzwebscraper.scraper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.skroutz.scraper.skroutzwebscraper.dto.pricehistory.PriceHistoryResponseDto;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Component
@RequiredArgsConstructor
@Slf4j
public class PriceHistoryScraper {

    private final WebClient webClient;

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
                    .timeout(Duration.ofSeconds(30))
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
