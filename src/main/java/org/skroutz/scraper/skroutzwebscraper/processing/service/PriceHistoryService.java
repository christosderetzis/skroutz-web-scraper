package org.skroutz.scraper.skroutzwebscraper.processing.service;

import lombok.extern.slf4j.Slf4j;
import org.skroutz.scraper.skroutzwebscraper.processing.entity.Product;
import org.skroutz.scraper.skroutzwebscraper.processing.repository.ProductRepository;
import org.skroutz.scraper.skroutzwebscraper.scraping.ScrapingService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class PriceHistoryService {

    private final ScrapingService scrapingService;
    private final ProductRepository productRepository;

    @Value("${price-history.delay-ms:1000}")
    private long delayMs;

    public PriceHistoryService(ScrapingService scrapingService,
                               ProductRepository productRepository) {
        this.scrapingService = scrapingService;
        this.productRepository = productRepository;
    }

    public void fetchPriceHistoryForProducts() {
        List<Product> products = productRepository.findAllByPriceHistoryParsed(false);

        for (Product product : products) {
            try {
                if (product.getUrl() == null || product.getUrl().isBlank()) {
                    log.warn("Product URL is empty or null for product ID: {}", product.getId());
                    continue;
                }

                scrapingService.scrapePriceHistory(product.getId(), product.getUrl());

                Thread.sleep(delayMs);
            } catch (Exception e) {
                log.error("Error processing product ID {}: {}", product.getId(), e.getMessage(), e);
            }
        }
    }
}
