package org.skroutz.scraper.skroutzwebscraper.scraper;

import org.skroutz.scraper.skroutzwebscraper.dto.PriceHistoryResponseDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class PriceHistoryScraper extends BaseScraper {

    public PriceHistoryScraper(WebClient webClient,
                               @Value("${scraper.timeout-seconds:30}") int timeoutSeconds,
                               @Value("${scraper.max-retries:3}") int maxRetries) {
        super(webClient, timeoutSeconds, maxRetries);
    }

    public PriceHistoryResponseDto fetchPriceHistory(String url) {
        return fetch(url, PriceHistoryResponseDto.class, "price history");
    }
}
