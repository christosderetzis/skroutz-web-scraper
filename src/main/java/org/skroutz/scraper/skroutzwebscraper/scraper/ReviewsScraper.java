package org.skroutz.scraper.skroutzwebscraper.scraper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.skroutz.scraper.skroutzwebscraper.dto.ReviewsApiResponseDto;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReviewsScraper {

    private final WebClient webClient;

    public List<ReviewsApiResponseDto.ReviewDto> scrapeReviews(String url) throws InterruptedException {
        log.info("Scraping reviews for {}", url);

        Integer offset = 0;
        Integer pageSize;
        List<ReviewsApiResponseDto.ReviewDto> reviewDtos = new ArrayList<>();
        String reviewUrl = buildReviewUrl(url, offset);

        do {
            Thread.sleep(100);
            ReviewsApiResponseDto response = fetchReviewPage(reviewUrl);
            List<ReviewsApiResponseDto.ReviewDto> reviewPageItems = response.getReviews().getReviews();
            reviewDtos.addAll(reviewPageItems);
            pageSize = reviewPageItems.size();
            offset += pageSize;
            reviewUrl = buildReviewUrl(url, offset);
        } while (pageSize > 0);

        log.info("Total reviews fetched: {}", reviewDtos.size());
        return reviewDtos;
    }

    public ReviewsApiResponseDto fetchReviewPage(String url) {
        log.debug("Fetching single review data from URL: {}", url);

        try {
            ReviewsApiResponseDto reviewsApiResponseDto = webClient
                    .get()
                    .uri(url)
                    .retrieve()
                    .onStatus(
                            status -> status.is4xxClientError() || status.is5xxServerError(),
                            clientResponse -> {
                                log.error("Error fetching review data. Status: {}", clientResponse.statusCode());
                                return Mono.error(new RuntimeException(
                                        "Failed to fetch review data from API"
                                ));
                            }
                    )
                    .bodyToMono(ReviewsApiResponseDto.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();

            if (reviewsApiResponseDto == null) {
                log.error("Received null response from review API");
                throw new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Failed to fetch review data: null response"
                );
            }

            log.debug("Successfully fetched review data from URL: {}", url);
            return reviewsApiResponseDto;
        } catch (Exception e) {
            log.error("Error fetching review data from URL: {}", url, e);
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to fetch review data: " + e.getMessage(),
                    e
            );
        }
    }

    private String buildReviewUrl(String productUrl, Integer offset) {
        int htmlIndex = productUrl.indexOf(".html");
        if (htmlIndex != -1) {
            productUrl = productUrl.substring(0, htmlIndex);
        }
        return "%s/reviews.json?offset=%d".formatted(productUrl, offset);
    }
}
