package org.skroutz.scraper.skroutzwebscraper.scraping.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.skroutz.scraper.skroutzwebscraper.scraping.application.service.orchestrator.PriceHistoryBatchOrchestrator;
import org.skroutz.scraper.skroutzwebscraper.scraping.application.service.orchestrator.ReviewsBatchOrchestrator;
import org.skroutz.scraper.skroutzwebscraper.scraping.application.service.orchestrator.SpecificationsBatchOrchestrator;
import org.skroutz.scraper.skroutzwebscraper.scraping.application.service.processing.ProductScraperService;
import org.skroutz.scraper.skroutzwebscraper.scraping.infrastructure.dto.ScraperRequestDto;
import org.springframework.modulith.events.EventPublication;
import org.springframework.modulith.events.core.EventPublicationRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncScrapingFacade {

    private static final long EVENT_DRAIN_TIMEOUT_MS = 20_000;
    private static final long EVENT_DRAIN_POLL_MS = 25;

    private final ScrapeJobService scrapeJobService;
    private final ProductScraperService productScraperService;
    private final ReviewsBatchOrchestrator reviewsBatchOrchestrator;
    private final SpecificationsBatchOrchestrator specificationsBatchOrchestrator;
    private final PriceHistoryBatchOrchestrator priceHistoryBatchOrchestrator;
    private final EventPublicationRepository eventPublicationRepository;

    @Async("scrapeTaskExecutor")
    public void runProductScraping(Long jobId, ScraperRequestDto request, boolean multiple) {
        try {
            productScraperService.scrapeProducts(request, multiple);
            awaitEventProcessing();
            scrapeJobService.completeJob(jobId);
        } catch (Exception e) {
            log.error("Product scraping job {} failed", jobId, e);
            scrapeJobService.failJob(jobId, e.getMessage());
        }
    }

    @Async("scrapeTaskExecutor")
    public void runReviewsScraping(Long jobId) {
        try {
            reviewsBatchOrchestrator.parseReviews();
            awaitEventProcessing();
            scrapeJobService.completeJob(jobId);
        } catch (Exception e) {
            log.error("Reviews scraping job {} failed", jobId, e);
            scrapeJobService.failJob(jobId, e.getMessage());
        }
    }

    @Async("scrapeTaskExecutor")
    public void runSpecificationsScraping(Long jobId) {
        try {
            specificationsBatchOrchestrator.parseSpecifications();
            awaitEventProcessing();
            scrapeJobService.completeJob(jobId);
        } catch (Exception e) {
            log.error("Specifications scraping job {} failed", jobId, e);
            scrapeJobService.failJob(jobId, e.getMessage());
        }
    }

    @Async("scrapeTaskExecutor")
    public void runPriceHistoryScraping(Long jobId) {
        try {
            priceHistoryBatchOrchestrator.fetchPriceHistoryForProducts();
            awaitEventProcessing();
            scrapeJobService.completeJob(jobId);
        } catch (Exception e) {
            log.error("Price history scraping job {} failed", jobId, e);
            scrapeJobService.failJob(jobId, e.getMessage());
        }
    }

    private void awaitEventProcessing() {
        long deadline = System.currentTimeMillis() + EVENT_DRAIN_TIMEOUT_MS;

        while (System.currentTimeMillis() < deadline) {
            if (eventPublicationRepository.countByStatus(EventPublication.Status.PROCESSING) == 0) {
                return;
            }

            try {
                Thread.sleep(EVENT_DRAIN_POLL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }

        log.warn("Timed out waiting for {} pending event publications to be processed.",
                eventPublicationRepository.countByStatus(EventPublication.Status.PROCESSING));
    }
}
