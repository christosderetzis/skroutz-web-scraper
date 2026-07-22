package org.skroutz.scraper.skroutzwebscraper.review.application.controller;

import lombok.extern.slf4j.Slf4j;
import org.skroutz.scraper.skroutzwebscraper.common.dto.PagedResponse;
import org.skroutz.scraper.skroutzwebscraper.review.application.service.ReviewsPersistenceService;
import org.skroutz.scraper.skroutzwebscraper.review.infrastructure.dto.ReviewResponseDto;
import org.skroutz.scraper.skroutzwebscraper.review.infrastructure.dto.ReviewSummaryDto;
import org.skroutz.scraper.skroutzwebscraper.review.application.service.ReviewsSummarizationService;
import org.skroutz.scraper.skroutzwebscraper.review.infrastructure.enums.ReviewSortType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/products")
public class ReviewsController {

    private final ReviewsSummarizationService reviewsSummarizationService;
    private final ReviewsPersistenceService reviewsService;

    public ReviewsController(ReviewsSummarizationService reviewsSummarizationService, ReviewsPersistenceService reviewsService) {
        this.reviewsSummarizationService = reviewsSummarizationService;
        this.reviewsService = reviewsService;
    }

    @PostMapping("/{id}/reviews/summarize")
    public ReviewSummaryDto summarizeReviews(@PathVariable Long id) {
        return reviewsSummarizationService.summarizeReviews(id);
    }

    @GetMapping("/{id}/reviews")
    public ResponseEntity<PagedResponse<ReviewResponseDto>> getProductReviews(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(value = "sort", defaultValue = "recent") String sortParam) {

        ReviewSortType sortType = ReviewSortType.fromString(sortParam);

        return ResponseEntity.ok(reviewsService.getReviewsByProductId(id, page, size, sortType));
    }
}
