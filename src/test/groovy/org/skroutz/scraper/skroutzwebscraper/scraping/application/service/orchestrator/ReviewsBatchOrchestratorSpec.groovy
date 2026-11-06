package org.skroutz.scraper.skroutzwebscraper.scraping.application.service.orchestrator

import org.skroutz.scraper.skroutzwebscraper.product.ProductDto
import org.skroutz.scraper.skroutzwebscraper.product.ProductFacade
import org.skroutz.scraper.skroutzwebscraper.scraping.application.service.processing.ReviewsScraperService
import org.skroutz.scraper.skroutzwebscraper.common.infrastructure.events.TransactionalEventPublisher
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.SliceImpl
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class ReviewsBatchOrchestratorSpec extends Specification {

    ProductFacade productFacade = Mock()
    ReviewsScraperService reviewsScraperService = Mock()
    TransactionalEventPublisher applicationEventPublisher = Mock()

    @Subject
    ReviewsBatchOrchestrator reviewsBatchOrchestrator

    def setup() {
        reviewsBatchOrchestrator = new ReviewsBatchOrchestrator(productFacade, reviewsScraperService, applicationEventPublisher)

        reviewsBatchOrchestrator.productLoopDelayMs = 0
    }

    @Unroll
    def "Should skip product when URL is '#urlScenario'"() {
        given: "A product with a bad URL"
            def product = new ProductDto(999L, urlValue, "iphone 18", "256gb ssd", "electronics")
            def slice = new SliceImpl([product], PageRequest.of(0, 100), false)

        when: "Service runs"
            reviewsBatchOrchestrator.parseReviews()

        then: "Scraper is never called"
            1 * productFacade.getUnparsedReviewsProducts(0, 100) >> slice
            0 * reviewsScraperService.scrapeProductReviews(_, _)

        where:
            urlScenario | urlValue
            "null"      | null
            "empty"     | ""
            "blank"     | "   "
    }

    def "Should handle interrupted exception during sleep"() {
        given: "A product"
            def product = new ProductDto(999L, "https://www.skroutz.gr/s/999/product-name.html", "iphone 18", "256gb ssd", "electronics")
            def slice = new SliceImpl([product], PageRequest.of(0, 100), false)
            productFacade.getUnparsedReviewsProducts(0, 100) >> slice

            // Re-initialize with delay to test interruption
            reviewsBatchOrchestrator.productLoopDelayMs = 1000

        when: "Thread is interrupted"
            Thread.currentThread().interrupt()
            reviewsBatchOrchestrator.parseReviews()

        then: "It completes without crashing and handles the flag"
            Thread.interrupted()
    }
}
