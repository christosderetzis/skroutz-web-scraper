package org.skroutz.scraper.skroutzwebscraper.processing.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.skroutz.scraper.skroutzwebscraper.processing.entity.Product;
import org.skroutz.scraper.skroutzwebscraper.processing.repository.ProductRepository;
import org.skroutz.scraper.skroutzwebscraper.scraping.event.SpecificationsScrapedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class SpecificationsEventListener {

    private final ProductRepository productRepository;

    @EventListener
    @Transactional
    public void handleSpecificationsScraped(SpecificationsScrapedEvent event) {
        log.info("Received SpecificationsScrapedEvent for product ID: {}", event.productId());

        Product product = productRepository.findById(event.productId())
                .orElseThrow(() -> new IllegalStateException("Product not found: " + event.productId()));

        product.setSpecifications(event.specifications());
        product.setSpecificationsParsed(true);
        productRepository.save(product);
        log.info("Successfully saved specifications for product: {}", product.getTitle());
    }
}
