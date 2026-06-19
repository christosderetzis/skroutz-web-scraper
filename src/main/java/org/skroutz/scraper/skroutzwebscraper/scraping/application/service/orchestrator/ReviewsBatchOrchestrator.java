package org.skroutz.scraper.skroutzwebscraper.scraping.application.service.orchestrator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.skroutz.scraper.skroutzwebscraper.product.domain.entity.Product;
import org.skroutz.scraper.skroutzwebscraper.product.domain.repository.ProductRepository;
import org.skroutz.scraper.skroutzwebscraper.scraping.infrastructure.dto.events.ReviewsScrapeResult;
import org.skroutz.scraper.skroutzwebscraper.review.application.service.ReviewsPersistenceService;
import org.skroutz.scraper.skroutzwebscraper.scraping.application.service.processing.ReviewsScraperService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewsBatchOrchestrator {

    private final ProductRepository productRepository;
    private final ReviewsScraperService scraperService;
    private final ReviewsPersistenceService persistenceService;

    @Value("${scraper.delays.reviews-ms:2000}")
    private long productLoopDelayMs;

    public void parseReviews() {
        log.info("Starting reviews scraping task...");
        Slice<Product> productSlice;
        int page = 0;

        do {
            productSlice = productRepository.findAllByReviewsParsedAndRatingIsNotNull(false, PageRequest.of(page, 100));

            for (Product product : productSlice) {
                if (product.getUrl() == null || product.getUrl().isBlank()) continue;

                // Step 1: Sequential Web Scraping (Completely outside DB transactional context)
                ReviewsScrapeResult result = scraperService.scrapeProductReviews(product.getId(), product.getUrl());

                // Step 2: Instant Database Persistence (Short-lived, clean transaction)
                // TODO: send an event when modulith split
                persistenceService.saveReviewsResult(result);

                takeBreather();
            }
            // Always move forward elegantly using the Slice framework features
        } while (productSlice.hasNext());
    }

    private void takeBreather() {
        try { Thread.sleep(productLoopDelayMs); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
