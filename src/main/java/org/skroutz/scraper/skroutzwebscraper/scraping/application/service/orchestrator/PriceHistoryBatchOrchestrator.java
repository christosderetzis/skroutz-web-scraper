package org.skroutz.scraper.skroutzwebscraper.scraping.application.service.orchestrator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.skroutz.scraper.skroutzwebscraper.product.ProductDto;
import org.skroutz.scraper.skroutzwebscraper.product.ProductFacade;
import org.skroutz.scraper.skroutzwebscraper.common.infrastructure.dto.events.PriceHistoryScrapeResult;
import org.skroutz.scraper.skroutzwebscraper.common.infrastructure.events.TransactionalEventPublisher;
import org.skroutz.scraper.skroutzwebscraper.scraping.application.service.processing.PriceHistoryScraperService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PriceHistoryBatchOrchestrator {

    private final ProductFacade productFacade;
    private final PriceHistoryScraperService scraperService;
    private final TransactionalEventPublisher eventPublisher;

    @Value("${scraper.delays.price-history-ms:1000}")
    private long delayMs;

    public void fetchPriceHistoryForProducts() {
        log.info("Starting price history scraping task...");

        Slice<ProductDto> productSlice;
        int page = 0;

        do {
            productSlice = productFacade.getUnparsedPriceHistoryProducts(page, 100);
            for (ProductDto product : productSlice) {
                if (product.url() == null || product.url().isBlank()) continue;

                // Step 1: Network Scrape (Slow I/O - entirely outside DB transactions)
                PriceHistoryScrapeResult result = scraperService.scrapeProductHistory(product.id(), product.url());

                // Step 2: Event Publish (short-lived transaction around the event publication)
                eventPublisher.publish(result);

                takeBreather();
            }
        } while (productSlice.hasNext());
    }

    private void takeBreather() {
        try { Thread.sleep(delayMs); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
