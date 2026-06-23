package org.skroutz.scraper.skroutzwebscraper.scraping.application.service.orchestrator


import org.skroutz.scraper.skroutzwebscraper.product.domain.entity.Product
import org.skroutz.scraper.skroutzwebscraper.product.domain.repository.ProductRepository
import org.skroutz.scraper.skroutzwebscraper.scraping.infrastructure.dto.events.PriceHistoryScrapeResult
import org.skroutz.scraper.skroutzwebscraper.scraping.application.service.orchestrator.PriceHistoryBatchOrchestrator
import org.skroutz.scraper.skroutzwebscraper.priceHistory.application.service.PriceHistoryPersistenceService
import org.skroutz.scraper.skroutzwebscraper.scraping.application.service.processing.PriceHistoryScraperService
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Slice
import org.springframework.data.domain.SliceImpl
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class PriceHistoryBatchOrchestratorSpec extends Specification {

    ProductRepository productRepository = Mock(ProductRepository)
    PriceHistoryScraperService scraperService = Mock(PriceHistoryScraperService)
    PriceHistoryPersistenceService persistenceService = Mock(PriceHistoryPersistenceService)

    @Subject
    PriceHistoryBatchOrchestrator priceHistoryBatchOrchestrator

    def setup() {
        priceHistoryBatchOrchestrator = new PriceHistoryBatchOrchestrator(productRepository, scraperService, persistenceService)
        priceHistoryBatchOrchestrator.delayMs = 0
    }

    def "Should handle interrupted exception during sleep"() {
        given: "A product"
            def product = Product.builder().id(1L).url("url").build()
            def slice = new SliceImpl([product], PageRequest.of(0, 100), false)
            productRepository.findAllByPriceHistoryParsed(false, PageRequest.of(0,100)) >> slice

            // Re-initialize with delay to test interruption
            priceHistoryBatchOrchestrator.delayMs = 1000

        when: "Thread is interrupted"
            Thread.currentThread().interrupt()
            priceHistoryBatchOrchestrator.fetchPriceHistoryForProducts()

        then: "It completes without crashing and handles the flag"
            Thread.interrupted()
    }

    @Unroll
    def "should skip processing and continue loop when product URL is '#urlDescription'"() {
        given: "A slice containing three products, where the middle one has an invalid URL"
            def validProduct1 = new Product(id: 1L, url: "http://example.com/p1")
            def invalidProduct = new Product(id: 2L, url: invalidUrl)
            def validProduct3 = new Product(id: 3L, url: "http://example.com/p3")

            List<Product> products = [validProduct1, invalidProduct, validProduct3]
            Slice<Product> mockSlice = new SliceImpl<>(products, PageRequest.of(0, 100), false)

        and: "The repository returns this slice on the first page call"
            productRepository.findAllByPriceHistoryParsed(false, PageRequest.of(0, 100)) >> mockSlice

        when: "The orchestrator executes the batch processing task"
            priceHistoryBatchOrchestrator.fetchPriceHistoryForProducts()

        then: "The valid products are scraped and persisted"
            1 * scraperService.scrapeProductHistory(1L, "http://example.com/p1") >> Mock(PriceHistoryScrapeResult)
            1 * scraperService.scrapeProductHistory(3L, "http://example.com/p3") >> Mock(PriceHistoryScrapeResult)
            2 * persistenceService.saveHistoryResult(_)

        and: "The invalid product completely skips scraping and persistence"
            0 * scraperService.scrapeProductHistory(2L, _)

        where:
            urlDescription | invalidUrl
            "null value"   | null
            "empty string" | ""
            "blank spaces" | "   "
    }
}
