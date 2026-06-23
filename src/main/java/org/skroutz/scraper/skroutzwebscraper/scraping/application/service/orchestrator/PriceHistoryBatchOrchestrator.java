package org.skroutz.scraper.skroutzwebscraper.scraping.application.service.orchestrator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.skroutz.scraper.skroutzwebscraper.product.domain.entity.Product;
import org.skroutz.scraper.skroutzwebscraper.product.domain.repository.ProductRepository;
import org.skroutz.scraper.skroutzwebscraper.scraping.infrastructure.dto.events.PriceHistoryScrapeResult;
import org.skroutz.scraper.skroutzwebscraper.priceHistory.application.service.PriceHistoryPersistenceService;
import org.skroutz.scraper.skroutzwebscraper.scraping.application.service.processing.PriceHistoryScraperService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PriceHistoryBatchOrchestrator {

    private final ProductRepository productRepository;
    private final PriceHistoryScraperService scraperService;
    private final PriceHistoryPersistenceService persistenceService;

    @Value("${scraper.delays.price-history-ms:1000}")
    private long delayMs;

    public void fetchPriceHistoryForProducts() {
        log.info("Starting price history scraping task...");

        Slice<Product> productSlice;
        int page = 0;

        do {
            productSlice = productRepository.findAllByPriceHistoryParsed(false, PageRequest.of(page, 100));

            for (Product product : productSlice) {
                if (product.getUrl() == null || product.getUrl().isBlank()) continue;

                // Step 1: Network Scrape (Slow I/O - entirely outside DB transactions)
                PriceHistoryScrapeResult result = scraperService.scrapeProductHistory(product.getId(), product.getUrl());

                // Step 2: DB Persistence (Blazing fast write transaction)
                persistenceService.saveHistoryResult(result);

                takeBreather();
            }
        } while (productSlice.hasNext());
    }

    private void takeBreather() {
        try { Thread.sleep(delayMs); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
