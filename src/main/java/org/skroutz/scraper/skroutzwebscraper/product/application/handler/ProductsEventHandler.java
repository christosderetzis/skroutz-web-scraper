package org.skroutz.scraper.skroutzwebscraper.product.application.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.skroutz.scraper.skroutzwebscraper.common.infrastructure.dto.events.PriceHistorySavedEvent;
import org.skroutz.scraper.skroutzwebscraper.product.ProductSearchEvent;
import org.skroutz.scraper.skroutzwebscraper.product.application.service.ProductPersistenceService;
import org.skroutz.scraper.skroutzwebscraper.product.domain.entity.Product;
import org.skroutz.scraper.skroutzwebscraper.product.domain.repository.ProductRepository;
import org.skroutz.scraper.skroutzwebscraper.review.ReviewsSavedEvent;
import org.skroutz.scraper.skroutzwebscraper.common.infrastructure.dto.events.ProductsScrapeResult;
import org.skroutz.scraper.skroutzwebscraper.common.infrastructure.dto.events.SpecificationsScrapeResult;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductsEventHandler {

    private final ProductRepository productRepository;
    private final ProductPersistenceService persistenceService;
    private final ApplicationEventPublisher eventPublisher;

    @EventListener
    public void markReviewsProductAsScraped(ReviewsSavedEvent event) {
        Product product = productRepository.findById(event.productId())
                .orElseThrow(() -> new IllegalArgumentException("Product target entity missing ID: " + event.productId()));

        product.setReviewsParsed(true);
        productRepository.save(product);
        log.info("Marked product ID {} as reviews parsed.", event.productId());
    }

    @EventListener
    public void markPriceHistoryProductAsScraped(PriceHistorySavedEvent event) {
        Product product = productRepository.findById(event.productId())
                .orElseThrow(() -> new IllegalArgumentException("Product target entity missing ID: " + event.productId()));

        product.setPriceHistoryParsed(true);
        productRepository.save(product);
        log.info("Marked product ID {} as price history parsed.", event.productId());
    }

    @EventListener
    public void parseSpecificationsEvent(SpecificationsScrapeResult results) {
        // Step 2: Persist Results (Database Operations)
        ProductSearchEvent productSearchEvent = persistenceService.saveSpecificationsResults(results);
        log.info("Processed specifications scrape results for {} products.", results.items().size());

        // Step 3: Publish Events for Elasticsearch Sync
        eventPublisher.publishEvent(productSearchEvent);
    }

    @EventListener
    public void handleProductsScrapeResultEvent(ProductsScrapeResult event) {
        persistenceService.saveOrUpdateProducts(event.productApiResponseDto(), event.category());
        log.info("Processed products scrape results for category: {}", event.category());
    }
}
