package org.skroutz.scraper.skroutzwebscraper.scraping.application.service.orchestrator

import org.skroutz.scraper.skroutzwebscraper.product.domain.entity.Product
import org.skroutz.scraper.skroutzwebscraper.product.domain.repository.ProductRepository
import org.skroutz.scraper.skroutzwebscraper.scraping.application.service.orchestrator.ReviewsBatchOrchestrator
import org.skroutz.scraper.skroutzwebscraper.review.application.service.ReviewsPersistenceService
import org.skroutz.scraper.skroutzwebscraper.scraping.application.service.processing.ReviewsScraperService
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.SliceImpl
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class ReviewsBatchOrchestratorSpec extends Specification {

    ProductRepository productRepository = Mock()
    ReviewsScraperService reviewsScraperService = Mock()
    ReviewsPersistenceService persistenceService = Mock()

    @Subject
    ReviewsBatchOrchestrator reviewsBatchOrchestrator;

    def setup() {
        reviewsBatchOrchestrator = new ReviewsBatchOrchestrator(productRepository, reviewsScraperService, persistenceService)

        reviewsBatchOrchestrator.productLoopDelayMs = 0
    }

    @Unroll
    def "Should skip product when URL is '#urlScenario'"() {
        given: "A product with a bad URL"
            def product = Product.builder().id(99L).url(urlValue).build()
            def slice = new SliceImpl([product], PageRequest.of(0, 100), false)

        when: "Service runs"
            reviewsBatchOrchestrator.parseReviews()

        then: "Scraper is never called"
            1 * productRepository.findAllByReviewsParsedAndRatingIsNotNull(false, PageRequest.of(0,100)) >> slice
            0 * reviewsScraperService.scrapeProductReviews(_, _)

        where:
            urlScenario | urlValue
            "null"      | null
            "empty"     | ""
            "blank"     | "   "
    }

    def "Should handle interrupted exception during sleep"() {
        given: "A product"
            def product = Product.builder().id(1L).url("url").build()
            def slice = new SliceImpl([product], PageRequest.of(0, 100), false)
            productRepository.findAllByReviewsParsedAndRatingIsNotNull(false, PageRequest.of(0,100)) >> slice

            // Re-initialize with delay to test interruption
            reviewsBatchOrchestrator.productLoopDelayMs = 1000

        when: "Thread is interrupted"
            Thread.currentThread().interrupt()
            reviewsBatchOrchestrator.parseReviews()

        then: "It completes without crashing and handles the flag"
            Thread.interrupted()
    }
}
