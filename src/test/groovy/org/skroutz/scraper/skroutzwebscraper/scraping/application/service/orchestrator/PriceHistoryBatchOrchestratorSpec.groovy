package org.skroutz.scraper.skroutzwebscraper.scraping.application.service.orchestrator


import org.skroutz.scraper.skroutzwebscraper.product.ProductDto
import org.skroutz.scraper.skroutzwebscraper.product.ProductFacade
import org.skroutz.scraper.skroutzwebscraper.common.infrastructure.dto.events.PriceHistoryScrapeResult
import org.skroutz.scraper.skroutzwebscraper.scraping.application.service.processing.PriceHistoryScraperService
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Slice
import org.springframework.data.domain.SliceImpl
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class PriceHistoryBatchOrchestratorSpec extends Specification {

    ProductFacade productFacade = Mock(ProductFacade)
    PriceHistoryScraperService scraperService = Mock(PriceHistoryScraperService)
    ApplicationEventPublisher eventPublisher = Mock(ApplicationEventPublisher)

    @Subject
    PriceHistoryBatchOrchestrator priceHistoryBatchOrchestrator

    def setup() {
        priceHistoryBatchOrchestrator = new PriceHistoryBatchOrchestrator(productFacade, scraperService, eventPublisher)
        priceHistoryBatchOrchestrator.delayMs = 0
    }

    def "Should handle interrupted exception during sleep"() {
        given: "A product"
            def product = randomProduct(1L, "http://example.com/product1")
            def slice = new SliceImpl([product], PageRequest.of(0, 100), false)
            productFacade.getUnparsedPriceHistoryProducts(0,100) >> slice

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
            def validProduct1 = randomProduct(1L, "http://example.com/p1")
            def invalidProduct = randomProduct(2L, invalidUrl)
            def validProduct3 = randomProduct(3L, "http://example.com/p3")

            List<ProductDto> products = [validProduct1, invalidProduct, validProduct3]
            Slice<ProductDto> mockSlice = new SliceImpl<>(products, PageRequest.of(0, 100), false)

        and: "The repository returns this slice on the first page call"
            productFacade.getUnparsedPriceHistoryProducts(0, 100) >> mockSlice

        when: "The orchestrator executes the batch processing task"
            priceHistoryBatchOrchestrator.fetchPriceHistoryForProducts()

        then: "The valid products are scraped and persisted"
            1 * scraperService.scrapeProductHistory(1L, "http://example.com/p1") >> Mock(PriceHistoryScrapeResult)
            1 * scraperService.scrapeProductHistory(3L, "http://example.com/p3") >> Mock(PriceHistoryScrapeResult)
            2 * eventPublisher.publishEvent(_)

        and: "The invalid product completely skips scraping and persistence"
            0 * scraperService.scrapeProductHistory(2L, _)

        where:
            urlDescription | invalidUrl
            "null value"   | null
            "empty string" | ""
            "blank spaces" | "   "
    }

    private ProductDto randomProduct(Long id, String url) {
        return new ProductDto(id, url, "iphone 18", "12gb ram, 256gb storage", "electronics")
    }
}
