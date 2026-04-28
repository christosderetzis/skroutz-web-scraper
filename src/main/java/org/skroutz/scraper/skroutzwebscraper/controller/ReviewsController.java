package org.skroutz.scraper.skroutzwebscraper.controller;

import jakarta.websocket.server.PathParam;
import lombok.extern.slf4j.Slf4j;
import org.skroutz.scraper.skroutzwebscraper.service.ReviewsService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/reviews")
public class ReviewsController {

    private final ReviewsService reviewsService;

    public ReviewsController(ReviewsService reviewsService) {
        this.reviewsService = reviewsService;
    }

    @PostMapping("{id}/summarize")
    public void summarizeReviews(@PathVariable Long id) {
        log.info("Starting reviews summarization task...");
        reviewsService.summarizeReviews(id);
        log.info("Reviews summarization task completed.");
    }
}
