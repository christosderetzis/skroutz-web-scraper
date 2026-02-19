
package org.skroutz.scraper.skroutzwebscraper.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.skroutz.scraper.skroutzwebscraper.entity.Product;
import org.skroutz.scraper.skroutzwebscraper.repository.ProductRepository;
import org.skroutz.scraper.skroutzwebscraper.scraper.SpecificationsScraper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SpecificationsService {

    private final ProductRepository productRepository;
    private final SpecificationsScraper specificationsScraper;
    private final ProductSearchService productSearchService;

    @Transactional
    public void parseSpecifications() {
        List<Product> unparsedProducts = productRepository.findAllBySpecificationsParsed(false);
        List<Product> successfullyParsedProducts = new ArrayList<>();

        for (Product product : unparsedProducts) {
            if (parseProductSpecifications(product)) {
                successfullyParsedProducts.add(product);
            }
        }

        if (!successfullyParsedProducts.isEmpty()) {
            productRepository.saveAll(successfullyParsedProducts);
            indexProducts(successfullyParsedProducts);
        }
    }

    private boolean parseProductSpecifications(Product product) {
        String url = product.getUrl();

        if (url == null || url.isBlank()) {
            log.warn("Product URL is empty or null for product: {}", product.getId());
            return false;
        }

        try {
            log.info("Parsing specifications for product: {}", product.getId());
            JsonNode specifications = specificationsScraper.scrapeSpecifications(url);

            if (specifications == null || specifications.isEmpty()) {
                log.warn("No specifications found for product: {}", product.getId());
                return false;
            }

            product.setSpecifications(specifications);
            product.setSpecificationsParsed(true);
            log.info("Successfully parsed specifications for product: {}", product.getTitle());
            return true;
        } catch (Exception e) {
            log.error("Error parsing specifications for product {}: {}", product.getId(), e.getMessage(), e);
            return false;
        }
    }

    private void indexProducts(List<Product> products) {
        try {
            productSearchService.indexProducts(products);
            log.info("Successfully indexed {} products to Elasticsearch", products.size());
        } catch (Exception e) {
            log.error("Error indexing products to Elasticsearch: {}", e.getMessage(), e);
        }
    }
}
