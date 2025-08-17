package org.skroutz.scraper.skroutzwebscraper.scheduled;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.skroutz.scraper.skroutzwebscraper.service.ReviewsService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class ReviewsScheduler {

    private final ReviewsService reviewsService;

    // Scheduled to run every 2 hours
    @Scheduled(fixedRate = 2 * 60 * 60 * 1000)
    public void parseReviews() {
        log.info("Starting reviews parsing task...");
        reviewsService.parseReviews();
        log.info("Reviews parsing task completed.");
    }
}
