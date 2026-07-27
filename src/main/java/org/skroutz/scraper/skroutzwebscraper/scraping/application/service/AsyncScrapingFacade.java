package org.skroutz.scraper.skroutzwebscraper.scraping.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.skroutz.scraper.skroutzwebscraper.scraping.application.service.orchestrator.PriceHistoryBatchOrchestrator;
import org.skroutz.scraper.skroutzwebscraper.scraping.application.service.orchestrator.ReviewsBatchOrchestrator;
import org.skroutz.scraper.skroutzwebscraper.scraping.application.service.orchestrator.SpecificationsBatchOrchestrator;
import org.skroutz.scraper.skroutzwebscraper.scraping.application.service.processing.ProductScraperService;
import org.skroutz.scraper.skroutzwebscraper.scraping.infrastructure.dto.ScraperRequestDto;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncScrapingFacade {

    private final ScrapeJobService scrapeJobService;
    private final ProductScraperService productScraperService;
    private final ReviewsBatchOrchestrator reviewsBatchOrchestrator;
    private final SpecificationsBatchOrchestrator specificationsBatchOrchestrator;
    private final PriceHistoryBatchOrchestrator priceHistoryBatchOrchestrator;

    @Async("scrapeTaskExecutor")
    public void runProductScraping(UUID jobId, ScraperRequestDto request, boolean multiple) {
        try {
            productScraperService.scrapeProducts(request, multiple);
            scrapeJobService.completeJob(jobId);
        } catch (Exception e) {
            log.error("Product scraping job {} failed", jobId, e);
            scrapeJobService.failJob(jobId, e.getMessage());
        }
    }

    @Async("scrapeTaskExecutor")
    public void runReviewsScraping(UUID jobId) {
        try {
            reviewsBatchOrchestrator.parseReviews();
            scrapeJobService.completeJob(jobId);
        } catch (Exception e) {
            log.error("Reviews scraping job {} failed", jobId, e);
            scrapeJobService.failJob(jobId, e.getMessage());
        }
    }

    @Async("scrapeTaskExecutor")
    public void runSpecificationsScraping(UUID jobId) {
        try {
            specificationsBatchOrchestrator.parseSpecifications();
            scrapeJobService.completeJob(jobId);
        } catch (Exception e) {
            log.error("Specifications scraping job {} failed", jobId, e);
            scrapeJobService.failJob(jobId, e.getMessage());
        }
    }

    @Async("scrapeTaskExecutor")
    public void runPriceHistoryScraping(UUID jobId) {
        try {
            priceHistoryBatchOrchestrator.fetchPriceHistoryForProducts();
            scrapeJobService.completeJob(jobId);
        } catch (Exception e) {
            log.error("Price history scraping job {} failed", jobId, e);
            scrapeJobService.failJob(jobId, e.getMessage());
        }
    }
}
