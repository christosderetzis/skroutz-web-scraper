package org.skroutz.scraper.skroutzwebscraper.scraper;

import org.skroutz.scraper.skroutzwebscraper.dto.ProductApiResponseDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class ProductsScraper extends BaseScraper {

    public ProductsScraper(WebClient webClient,
                           @Value("${scraper.timeout-millis:30000}") long timeoutMillis,
                           @Value("${scraper.retry-delay-ms:1000}") int retryDelayMillis,
                           @Value("${scraper.max-retries:3}") int maxRetries) {
        super(webClient, timeoutMillis, retryDelayMillis, maxRetries);
    }

    public ProductApiResponseDto fetchProductsPage(String url) {
        return fetch(url, ProductApiResponseDto.class, "products");
    }
}
