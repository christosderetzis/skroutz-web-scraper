package org.skroutz.scraper.skroutzwebscraper.scraping.application.service.orchestrator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.skroutz.scraper.skroutzwebscraper.category.CategorySchemaFacade;
import org.skroutz.scraper.skroutzwebscraper.category.domain.schema.CategoryMappingSchema;
import org.skroutz.scraper.skroutzwebscraper.product.ProductDto;
import org.skroutz.scraper.skroutzwebscraper.product.ProductFacade;
import org.skroutz.scraper.skroutzwebscraper.scraping.application.service.processing.SpecificationsScraperService;
import org.skroutz.scraper.skroutzwebscraper.common.infrastructure.dto.events.SpecificationsScrapeResult;
import org.skroutz.scraper.skroutzwebscraper.common.infrastructure.events.TransactionalEventPublisher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SpecificationsBatchOrchestrator {

    private final ProductFacade productFacade;
    private final CategorySchemaFacade categorySchemaFacade;
    private final SpecificationsScraperService scraperService;
    private final TransactionalEventPublisher eventPublisher;

    @Value("${scraper.specifications.batch-size:30}")
    private Integer batchSize;

    public void parseSpecifications() {
        int batchNumber = 0;
        int totalProcessed = 0;

        // Load and cache schemas for this specific batch execution run
        Map<String, CategoryMappingSchema> schemasByCategory = categorySchemaFacade.getAllMappingSchemas();
        log.info("Loaded {} category schemas", schemasByCategory.size());

        Slice<ProductDto> productPage;
        do {
            // Always query page 0 because processed items fall out of the query scope
            Pageable pageable = PageRequest.of(0, batchSize);
            productPage = productFacade.getUnparsedSpecificationsProducts(0, 100);

            if (productPage.isEmpty()) {
                break;
            }

            batchNumber++;
            log.info("Processing batch {} with {} products", batchNumber, productPage.getNumberOfElements());

            // Step 1: Scrape & Normalize (Network Operations)
            SpecificationsScrapeResult scrapeResults = scraperService.scrapeBatch(productPage.getContent(), schemasByCategory);

            // Step 2: Persist Results (Database Operations)
            eventPublisher.publish(scrapeResults);

            totalProcessed += productPage.getNumberOfElements();

        } while (!productPage.isEmpty());

        log.info("Completed specifications parsing. Total processed: {}", totalProcessed);
    }
}
