package org.skroutz.scraper.skroutzwebscraper.processing.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.skroutz.scraper.skroutzwebscraper.processing.entity.Product;
import org.skroutz.scraper.skroutzwebscraper.processing.repository.ProductRepository;
import org.skroutz.scraper.skroutzwebscraper.scraping.ScrapingService;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SpecificationsService {

    private final ProductRepository productRepository;
    private final ScrapingService scrapingService;

    public void parseSpecifications() {
        List<Product> unparsedProducts = productRepository.findAllBySpecificationsParsed(false);
        for (Product product : unparsedProducts) {
            try {
                log.info("Parsing specifications for product: {}", product.getId());
                String url = product.getUrl();
                if (url != null && !url.isBlank()) {
                    scrapingService.scrapeSpecifications(product.getId(), url);
                    log.info("Triggered specifications scraping for product: {}", product.getId());
                } else {
                    log.warn("Product URL is empty or null for product: {}", product.getId());
                }
            } catch (Exception e) {
                log.error("Error parsing specifications for product {}: {}", product.getId(), e.getMessage(), e);
            }
        }
    }
}
