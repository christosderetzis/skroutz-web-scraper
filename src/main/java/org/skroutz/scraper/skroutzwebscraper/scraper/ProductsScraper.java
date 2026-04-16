package org.skroutz.scraper.skroutzwebscraper.scraper;

import lombok.extern.slf4j.Slf4j;
import org.skroutz.scraper.skroutzwebscraper.dto.ProductApiResponseDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;

@Component
@Slf4j
public class ProductsScraper {

    private final WebClient webClient;
    private final int timeoutSeconds;

    public ProductsScraper(WebClient webClient, @Value("${scraper.timeout-seconds:30}") int timeoutSeconds) {
        this.webClient = webClient;
        this.timeoutSeconds = timeoutSeconds;
    }

    public ProductApiResponseDto fetchProductsPage(String url) {
        log.info("Fetching product data from URL: {}", url);

        try {
            ProductApiResponseDto response = webClient.get()
                    .uri(url)
                    .retrieve()
                    .onStatus(
                            status -> status.is4xxClientError() || status.is5xxServerError(),
                            clientResponse -> {
                                log.error("Error fetching products. Status: {}", clientResponse.statusCode());
                                return Mono.error(new ResponseStatusException(
                                        clientResponse.statusCode(),
                                        "Failed to fetch products from API"
                                ));
                            }
                    )
                    .bodyToMono(ProductApiResponseDto.class)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .retryWhen(Retry.fixedDelay(3, Duration.ofSeconds(2))
                            .filter(throwable -> {
                                if (throwable instanceof ResponseStatusException rse) {
                                    return rse.getStatusCode() == HttpStatus.FORBIDDEN;
                                }
                                if (throwable instanceof WebClientResponseException wcre) {
                                    return wcre.getStatusCode() == HttpStatus.FORBIDDEN;
                                }
                                return false;
                            })
                            .doBeforeRetry(retrySignal ->
                                log.warn("Received 403 Forbidden. Retrying attempt {}/3 after 2 seconds...",
                                    retrySignal.totalRetries() + 1)
                            )
                            .onRetryExhaustedThrow((retryBackoffSpec, retrySignal) -> {
                                log.error("Max retries (3) exhausted for URL: {}", url);
                                return retrySignal.failure();
                            })
                    )
                    .block();

            if (response == null) {
                log.error("Received null response from products API");
                throw new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Failed to fetch products: null response"
                );
            }

            log.warn("Successfully fetched product page data");
            return response;

        } catch (Exception e) {
            log.error("Error fetching products from URL: {}", url, e);
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to fetch products: " + e.getMessage(),
                    e
            );
        }
    }
}
