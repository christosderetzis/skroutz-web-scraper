package org.skroutz.scraper.skroutzwebscraper.scraping.application.service.orchestrator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.skroutz.scraper.skroutzwebscraper.product.ProductDto;
import org.skroutz.scraper.skroutzwebscraper.product.ProductFacade;
import org.skroutz.scraper.skroutzwebscraper.common.infrastructure.dto.events.ReviewsScrapeResult;
import org.skroutz.scraper.skroutzwebscraper.common.infrastructure.events.TransactionalEventPublisher;
import org.skroutz.scraper.skroutzwebscraper.scraping.application.service.processing.ReviewsScraperService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewsBatchOrchestrator {

    private final ProductFacade productFacade;
    private final ReviewsScraperService scraperService;
    private final TransactionalEventPublisher eventPublisher;

    @Value("${scraper.delays.reviews-ms:2000}")
    private long productLoopDelayMs;

    public void parseReviews() {
        log.info("Starting reviews scraping task...");
        Slice<ProductDto> productSlice;
        int page = 0;

        do {
            productSlice = productFacade.getUnparsedReviewsProducts(page, 100);

            for (ProductDto product : productSlice) {
                if (product.url() == null || product.url().isBlank()) continue;

                // Step 1: Sequential Web Scraping (Completely outside DB transactional context)
                ReviewsScrapeResult result = scraperService.scrapeProductReviews(product.id(), product.url());

                // Step 2: Instant Database Persistence (Short-lived, clean transaction)
                eventPublisher.publish(result);
                takeBreather();
            }
            // Always move forward elegantly using the Slice framework features
        } while (productSlice.hasNext());
    }

    private void takeBreather() {
        try { Thread.sleep(productLoopDelayMs); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
