package org.skroutz.scraper.skroutzwebscraper.review.application.controller;

import lombok.extern.slf4j.Slf4j;
import org.skroutz.scraper.skroutzwebscraper.review.infrastructure.dto.ReviewSummaryDto;
import org.skroutz.scraper.skroutzwebscraper.review.application.service.ReviewsSummarizationService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/reviews")
public class ReviewsController {

    private final ReviewsSummarizationService reviewsSummarizationService;

    public ReviewsController(ReviewsSummarizationService reviewsSummarizationService) {
        this.reviewsSummarizationService = reviewsSummarizationService;
    }

    @PostMapping("/{id}/summarize")
    public ReviewSummaryDto summarizeReviews(@PathVariable Long id) {
        return reviewsSummarizationService.summarizeReviews(id);
    }
}
