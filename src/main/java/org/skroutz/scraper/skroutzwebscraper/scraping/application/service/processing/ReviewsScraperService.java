package org.skroutz.scraper.skroutzwebscraper.scraping.application.service.processing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.skroutz.scraper.skroutzwebscraper.scraping.infrastructure.dto.api.ReviewsApiResponseDto;
import org.skroutz.scraper.skroutzwebscraper.scraping.infrastructure.dto.events.ReviewsScrapeResult;
import org.skroutz.scraper.skroutzwebscraper.scraping.infrastructure.scraper.ReviewsScraper;
import org.skroutz.scraper.skroutzwebscraper.scraping.infrastructure.utils.UrlBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewsScraperService {

    private final ReviewsScraper reviewsScraper;
    private final UrlBuilder urlBuilder;

    @Value("${scraper.delays.review-page-ms:100}")
    private long reviewPageDelayMs;

    public ReviewsScrapeResult scrapeProductReviews(Long productId, String productUrl) {
        log.info("Scraping reviews via API for product ID: {}", productId);

        int offset = 0;
        int pageSize;
        List<ReviewsApiResponseDto.ReviewDto> dtoList = new ArrayList<>();

        try {
            do {
                // Safety back-off delay happens here completely away from any database hooks
                sleep(reviewPageDelayMs);

                String reviewUrl = urlBuilder.buildReviewsApiUrl(productUrl, offset);
                ReviewsApiResponseDto response = reviewsScraper.fetchReviewPage(reviewUrl);

                if (response == null || response.getReviews() == null || response.getReviews().getReviews() == null) {
                    break;
                }

                List<ReviewsApiResponseDto.ReviewDto> pageItems = response.getReviews().getReviews();
                dtoList.addAll(pageItems);

                pageSize = pageItems.size();
                offset += pageSize;
            } while (pageSize > 0);

            log.info("Total reviews fetched for product ID {}: {}", productId, dtoList.size());
            return new ReviewsScrapeResult(productId, dtoList, true);

        } catch (Exception e) {
            log.error("Network scraping failed for product ID {}: {}", productId, e.getMessage());
            return new ReviewsScrapeResult(productId, Collections.emptyList(), false);
        }
    }

    private void sleep(long millis) {
        try { Thread.sleep(millis); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
