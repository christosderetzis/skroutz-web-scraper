package org.skroutz.scraper.skroutzwebscraper.scraper;

import org.skroutz.scraper.skroutzwebscraper.dto.ReviewsApiResponseDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class ReviewsScraper extends BaseScraper {

    public ReviewsScraper(WebClient webClient,
                          @Value("${scraper.timeout-seconds:30}") int timeoutSeconds,
                          @Value("${scraper.max-retries:3}") int maxRetries) {
        super(webClient, timeoutSeconds, maxRetries);
    }

    public ReviewsApiResponseDto fetchReviewPage(String url) {
        return fetch(url, ReviewsApiResponseDto.class, "reviews");
    }
}
