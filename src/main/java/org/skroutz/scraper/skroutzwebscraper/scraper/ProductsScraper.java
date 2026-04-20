package org.skroutz.scraper.skroutzwebscraper.scraper;

import org.skroutz.scraper.skroutzwebscraper.dto.ProductApiResponseDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class ProductsScraper extends BaseScraper {

    public ProductsScraper(WebClient webClient,
                           @Value("${scraper.timeout-seconds:30}") int timeoutSeconds,
                           @Value("${scraper.max-retries:3}") int maxRetries) {
        super(webClient, timeoutSeconds, maxRetries);
    }

    public ProductApiResponseDto fetchProductsPage(String url) {
        return fetch(url, ProductApiResponseDto.class, "products");
    }
}
