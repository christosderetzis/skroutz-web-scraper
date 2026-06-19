package org.skroutz.scraper.skroutzwebscraper.scraping.application.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.skroutz.scraper.skroutzwebscraper.scraping.infrastructure.dto.ScraperRequestDto;
import org.skroutz.scraper.skroutzwebscraper.scraping.application.service.orchestrator.PriceHistoryBatchOrchestrator;
import org.skroutz.scraper.skroutzwebscraper.scraping.application.service.orchestrator.ReviewsBatchOrchestrator;
import org.skroutz.scraper.skroutzwebscraper.scraping.application.service.orchestrator.SpecificationsBatchOrchestrator;
import org.skroutz.scraper.skroutzwebscraper.scraping.application.service.processing.ProductScraperService;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/scraper")
@RequiredArgsConstructor
@Validated
public class ScraperController {

    private final SpecificationsBatchOrchestrator specificationsBatchOrchestrator;
    private final ReviewsBatchOrchestrator reviewsBatchOrchestrator;
    private final ProductScraperService productScraperService;
    private final PriceHistoryBatchOrchestrator priceHistoryBatchOrchestrator;

    @PostMapping("/specifications")
    public void scrapeSpecifications() {
        log.info("Starting specifications scraping task...");
        specificationsBatchOrchestrator.parseSpecifications();
        log.info("Specifications scraping task completed.");
    }

    @PostMapping("/reviews")
    public void scrapeReviews() {
        log.info("Starting reviews scraping task...");
        reviewsBatchOrchestrator.parseReviews();
        log.info("Reviews scraping task completed.");
    }

    @PostMapping("/price-history")
    public void scrapePriceHistory() {
        log.info("Starting price history scraping task...");
        priceHistoryBatchOrchestrator.fetchPriceHistoryForProducts();
        log.info("Price history scraping task completed.");
    }

    @PostMapping("/products")
    public ResponseEntity<Void> scrapeProducts(@Valid @RequestBody ScraperRequestDto request,
                                                @RequestParam boolean multiple) {
        log.info("Received request to scrape products from URL: {}, multiple: {}", request.getUrl(), multiple);
        productScraperService.scrapeProducts(request, multiple);
        return ResponseEntity.ok().build();
    }
}
