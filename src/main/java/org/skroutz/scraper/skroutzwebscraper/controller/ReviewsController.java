package org.skroutz.scraper.skroutzwebscraper.controller;

import lombok.extern.slf4j.Slf4j;
import org.skroutz.scraper.skroutzwebscraper.dto.ReviewSummary;
import org.skroutz.scraper.skroutzwebscraper.service.ReviewsSummarizationService;
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

    @PostMapping("{id}/summarize")
    public ReviewSummary summarizeReviews(@PathVariable Long id) {
        return reviewsSummarizationService.summarizeReviews(id);
    }
}
