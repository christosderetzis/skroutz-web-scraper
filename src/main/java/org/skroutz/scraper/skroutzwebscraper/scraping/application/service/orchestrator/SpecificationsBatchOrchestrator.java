package org.skroutz.scraper.skroutzwebscraper.scraping.application.service.orchestrator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.skroutz.scraper.skroutzwebscraper.scraping.infrastructure.dto.events.SpecificationsScrapeResult;
import org.skroutz.scraper.skroutzwebscraper.category.domain.entity.CategorySchema;
import org.skroutz.scraper.skroutzwebscraper.product.domain.entity.Product;
import org.skroutz.scraper.skroutzwebscraper.category.domain.repository.CategorySchemaRepository;
import org.skroutz.scraper.skroutzwebscraper.product.domain.repository.ProductRepository;
import org.skroutz.scraper.skroutzwebscraper.search.application.service.ProductIndexingService;
import org.skroutz.scraper.skroutzwebscraper.product.application.service.ProductPersistenceService;
import org.skroutz.scraper.skroutzwebscraper.scraping.application.service.processing.SpecificationsScraperService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SpecificationsBatchOrchestrator {

    private final ProductRepository productRepository;
    private final CategorySchemaRepository categorySchemaRepository;

    private final SpecificationsScraperService scraperService;
    private final ProductPersistenceService persistenceService;
    private final ProductIndexingService indexingService;

    @Value("${scraper.specifications.batch-size:30}")
    private Integer batchSize;

    public void parseSpecifications() {
        int batchNumber = 0;
        int totalProcessed = 0;
        int totalSuccessful = 0;

        // Load and cache schemas for this specific batch execution run
        Map<String, CategorySchema> schemasByCategory = categorySchemaRepository.findAll().stream()
                .collect(Collectors.toMap(CategorySchema::getCategory, s -> s));
        log.info("Loaded {} category schemas", schemasByCategory.size());

        Page<Product> productPage;
        do {
            // Always query page 0 because processed items fall out of the query scope
            Pageable pageable = PageRequest.of(0, batchSize);
            productPage = productRepository.findAllBySpecificationsIsNullAndSpecificationsSkippedIsFalseOrderByIdAsc(pageable);

            if (productPage.isEmpty()) {
                break;
            }

            batchNumber++;
            log.info("Processing batch {} with {} products", batchNumber, productPage.getNumberOfElements());

            // Step 1: Scrape & Normalize (Network Operations)
            List<SpecificationsScrapeResult> scrapeResults = scraperService.scrapeBatch(productPage.getContent(), schemasByCategory);

            // Step 2: Persist state changes to DB (Database Transaction)
            // TODO: emit event during modulith split
            List<Product> productsToSync = persistenceService.saveSpecificationsResults(scrapeResults);

            // Step 3: Index successfully updated items (Search Infrastructure)
            indexingService.indexProducts(productsToSync);

            totalSuccessful += productsToSync.size();
            totalProcessed += productPage.getNumberOfElements();

        } while (!productPage.isEmpty());

        log.info("Completed specifications parsing. Total processed: {}, Total successful: {}",
                totalProcessed, totalSuccessful);
    }
}
