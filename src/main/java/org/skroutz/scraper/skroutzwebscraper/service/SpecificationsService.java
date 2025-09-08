package org.skroutz.scraper.skroutzwebscraper.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.skroutz.scraper.skroutzwebscraper.entity.Product;
import org.skroutz.scraper.skroutzwebscraper.repository.ProductRepository;
import org.skroutz.scraper.skroutzwebscraper.scraper.SpecificationsScraper;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SpecificationsService {

    private final ProductRepository productRepository;
    private final SpecificationsScraper specificationsScraper;

    public void parseSpecifications() {
        List<Product> unparsedProducts = productRepository.findAllBySpecificationsParsed(false);
        for (Product product : unparsedProducts) {
            try {
                log.info("Parsing specifications for product: {}", product.getId());
                String url = product.getUrl();
                if (!url.isBlank()) {
                    JsonNode specifications = specificationsScraper.screapeSpecifications(url);
                    product.setSpecifications(specifications);
                    product.setSpecificationsParsed(true);
                    productRepository.save(product);
                    log.info("Successfully parsed specifications for product: {}", product.getTitle());
                } else {
                    log.warn("Product URL is empty or null for product: {}", product.getId());
                }
            } catch (Exception e) {
                log.error("Error parsing specifications for product {}: {}", product.getId(), e.getMessage(), e);
            }
        }
    }
}
