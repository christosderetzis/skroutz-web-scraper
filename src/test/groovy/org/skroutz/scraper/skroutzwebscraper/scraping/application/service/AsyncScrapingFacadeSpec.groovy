package org.skroutz.scraper.skroutzwebscraper.scraping.application.service

import org.skroutz.scraper.skroutzwebscraper.scraping.application.service.orchestrator.PriceHistoryBatchOrchestrator
import org.skroutz.scraper.skroutzwebscraper.scraping.application.service.orchestrator.ReviewsBatchOrchestrator
import org.skroutz.scraper.skroutzwebscraper.scraping.application.service.orchestrator.SpecificationsBatchOrchestrator
import org.skroutz.scraper.skroutzwebscraper.scraping.application.service.processing.ProductScraperService
import org.skroutz.scraper.skroutzwebscraper.scraping.infrastructure.dto.ScraperRequestDto
import spock.lang.Specification

class AsyncScrapingFacadeSpec extends Specification {

    ScrapeJobService scrapeJobService = Mock()
    ProductScraperService productScraperService = Mock()
    ReviewsBatchOrchestrator reviewsBatchOrchestrator = Mock()
    SpecificationsBatchOrchestrator specificationsBatchOrchestrator = Mock()
    PriceHistoryBatchOrchestrator priceHistoryBatchOrchestrator = Mock()

    AsyncScrapingFacade facade = new AsyncScrapingFacade(
            scrapeJobService,
            productScraperService,
            reviewsBatchOrchestrator,
            specificationsBatchOrchestrator,
            priceHistoryBatchOrchestrator
    )

    def jobId = UUID.randomUUID()

    def "runProductScraping fails job when scraping throws"() {
        given:
            def request = ScraperRequestDto.builder().url("https://example.com").category("phones").build()
            productScraperService.scrapeProducts(_, _) >> { throw new RuntimeException("network error") }

        when:
            facade.runProductScraping(jobId, request, true)

        then:
            1 * scrapeJobService.failJob(jobId, "network error")
            0 * scrapeJobService.completeJob(_)
    }

    def "runReviewsScraping fails job when orchestrator throws"() {
        given:
            reviewsBatchOrchestrator.parseReviews() >> { throw new RuntimeException("DB error") }

        when:
            facade.runReviewsScraping(jobId)

        then:
            1 * scrapeJobService.failJob(jobId, "DB error")
            0 * scrapeJobService.completeJob(_)
    }

    def "runSpecificationsScraping fails job when orchestrator throws"() {
        given:
            specificationsBatchOrchestrator.parseSpecifications() >> { throw new RuntimeException("timeout") }

        when:
            facade.runSpecificationsScraping(jobId)

        then:
            1 * scrapeJobService.failJob(jobId, "timeout")
    }

    def "runPriceHistoryScraping fails job when orchestrator throws"() {
        given:
            priceHistoryBatchOrchestrator.fetchPriceHistoryForProducts() >> { throw new RuntimeException("503") }

        when:
            facade.runPriceHistoryScraping(jobId)

        then:
            1 * scrapeJobService.failJob(jobId, "503")
    }
}
