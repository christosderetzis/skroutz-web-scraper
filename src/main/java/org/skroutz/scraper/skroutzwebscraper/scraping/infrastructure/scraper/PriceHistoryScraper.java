package org.skroutz.scraper.skroutzwebscraper.scraping.infrastructure.scraper;

import org.skroutz.scraper.skroutzwebscraper.scraping.infrastructure.dto.api.PriceHistoryResponseApiDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class PriceHistoryScraper extends BaseScraper {

    @Autowired
    public PriceHistoryScraper(WebClient webClient,
                               FlareSolverrClient flareSolverrClient,
                                   @Value("${scraper.timeout-millis:30000}") long timeoutMillis,
                               @Value("${scraper.retry-delay-ms:1000}") int retryDelayMillis,
                               @Value("${scraper.max-retries:3}") int maxRetries) {
        super(webClient, flareSolverrClient, timeoutMillis, retryDelayMillis, maxRetries);
    }

    public PriceHistoryScraper(WebClient webClient,
                               long timeoutMillis,
                               int retryDelayMillis,
                               int maxRetries) {
        super(webClient, timeoutMillis, retryDelayMillis, maxRetries);
    }

    public PriceHistoryResponseApiDto fetchPriceHistory(String url) {
        return fetch(url, PriceHistoryResponseApiDto.class, "price history");
    }
}
