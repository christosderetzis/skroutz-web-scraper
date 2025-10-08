package org.skroutz.scraper.skroutzwebscraper.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.skroutz.scraper.skroutzwebscraper.service.ReviewsService;
import org.skroutz.scraper.skroutzwebscraper.service.SpecificationsService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/scraper")
@RequiredArgsConstructor
public class ScraperController {

    private final SpecificationsService specificationsService;
    private final ReviewsService reviewsService;

    @PostMapping("/specifications")
    public void scrapeSpecifications() {
        log.info("Starting specifications scraping task...");
        specificationsService.parseSpecifications();
        log.info("Specifications scraping task completed.");
    }

    @PostMapping("/reviews")
    public void scrapeReviews() {
        log.info("Starting reviews scraping task...");
        reviewsService.parseReviews();
        log.info("Reviews scraping task completed.");
    }
}
