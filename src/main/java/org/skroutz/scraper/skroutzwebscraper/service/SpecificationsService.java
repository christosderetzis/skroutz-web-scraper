package org.skroutz.scraper.skroutzwebscraper.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.skroutz.scraper.skroutzwebscraper.entity.Product;
import org.skroutz.scraper.skroutzwebscraper.repository.ProductRepository;
import org.skroutz.scraper.skroutzwebscraper.scraper.SpecificationsScraper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class SpecificationsService {

    private final ProductRepository productRepository;
    private final SpecificationsScraper specificationsScraper;
    private final ProductSearchService productSearchService;
    private final Integer batchSize;

    public SpecificationsService(ProductRepository productRepository, SpecificationsScraper specificationsScraper, ProductSearchService productSearchService,
                                 @Value("${scraper.specifications.batch-size:30}") Integer size) {
        this.productRepository = productRepository;
        this.specificationsScraper = specificationsScraper;
        this.productSearchService = productSearchService;
        this.batchSize = size;
    }

    public void parseSpecifications() {
        int batchNumber = 0;
        int totalProcessed = 0;
        int totalSuccessful = 0;

        Page<Product> productPage;
        do {
            // Always query page 0 since processed products are removed from results
            Pageable pageable = PageRequest.of(0, batchSize);
            productPage = productRepository.findAllBySpecificationsIsNullAndSpecificationsSkippedIsFalseOrderByIdAsc(pageable);

            if (productPage.isEmpty()) {
                break;
            }

            batchNumber++;
            log.info("Processing batch {} with {} products", batchNumber, productPage.getNumberOfElements());

            List<Product> successfulProducts = processBatch(productPage.getContent());

            // Save all products (mark them as parsed to avoid infinite loop)
            productRepository.saveAll(productPage.getContent());

            // Only index successful products
            if (!successfulProducts.isEmpty()) {
                indexProducts(successfulProducts);
                totalSuccessful += successfulProducts.size();
            }

            totalProcessed += productPage.getNumberOfElements();

        } while (!productPage.isEmpty());

        log.info("Completed specifications parsing. Total processed: {}, Total successful: {}",
                totalProcessed, totalSuccessful);
    }

    private List<Product> processBatch(List<Product> products) {
        List<Product> successfulProducts = new ArrayList<>();

        for (Product product : products) {
            if (parseProductSpecifications(product)) {
                successfulProducts.add(product);
            } else {
                // Mark as skipped to prevent infinite loop (leave specifications as NULL for retry)
                product.setSpecificationsSkipped(true);
            }
        }

        return successfulProducts;
    }

    private boolean parseProductSpecifications(Product product) {
        String url = product.getUrl();

        if (url == null || url.isBlank()) {
            log.warn("Product URL is empty or null for product: {}", product.getId());
            return false;
        }

        try {
            log.info("Parsing specifications for product: {}", product.getId());
            String formattedUrl = url.contains("?") ? url + "&lang=en" : url + "?lang=en";

            // Add a short delay to avoid overwhelming the target server
            Thread.sleep(500);
            JsonNode specifications = specificationsScraper.scrapeSpecifications(formattedUrl);

            if (specifications == null || specifications.isEmpty()) {
                log.warn("No specifications found for product: {}", product.getId());
                return false;
            }

            product.setSpecifications(specifications);
            product.setSpecificationsSkipped(false); // Reset flag on successful parse
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
