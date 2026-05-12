package org.skroutz.scraper.skroutzwebscraper.service

import ch.qos.logback.classic.Level
import org.skroutz.scraper.skroutzwebscraper.base.WithLoggingBaseSpec
import org.skroutz.scraper.skroutzwebscraper.dto.ReviewsApiResponseDto
import org.skroutz.scraper.skroutzwebscraper.entity.Product
import org.skroutz.scraper.skroutzwebscraper.entity.Review
import org.skroutz.scraper.skroutzwebscraper.mapper.ReviewsMapper
import org.skroutz.scraper.skroutzwebscraper.repository.ProductRepository
import org.skroutz.scraper.skroutzwebscraper.repository.ReviewRepository
import org.skroutz.scraper.skroutzwebscraper.scraper.ReviewsScraper
import org.skroutz.scraper.skroutzwebscraper.utils.UrlBuilder
import org.springframework.transaction.support.TransactionTemplate
import spock.lang.Subject
import spock.lang.Unroll

import java.util.function.Consumer

class ReviewsServiceSpec extends WithLoggingBaseSpec {

    // All dependencies must now be mocked since they are injected into one service
    ReviewsScraper reviewsScraper = Mock()
    ReviewsMapper reviewsMapper = Mock()
    ReviewRepository reviewRepository = Mock()
    ProductRepository productRepository = Mock()
    UrlBuilder urlBuilder = Mock()
    TransactionTemplate transactionTemplate = Mock()

    @Subject
    ReviewsService reviewsService

    def setup() {
        // Initialize with 0 delays for fast tests
        reviewsService = new ReviewsService(
                reviewsScraper,
                reviewsMapper,
                reviewRepository,
                productRepository,
                urlBuilder,
                transactionTemplate
        )

        reviewsService.reviewPageDelayMs = 0
        reviewsService.productLoopDelayMs = 0

        // CRITICAL: Force the TransactionTemplate to actually run the logic inside it
        transactionTemplate.executeWithoutResult(_ as Consumer) >> { Consumer c ->
            c.accept(null)
        }
    }

    def "Happy path: parse reviews for multiple products with pagination"() {
        given: "Two products waiting to be parsed"
            def p1 = Product.builder().id(1L).url("http://url1.com").reviewsParsed(false).build()
            def p2 = Product.builder().id(2L).url("http://url2.com").reviewsParsed(false).build()
            productRepository.findAllByReviewsParsedAndRatingIsNotNull(false) >> [p1, p2]

        and: "Responses for p1 (2 pages) and p2 (1 page)"
            def p1Page1 = createResponse([new ReviewsApiResponseDto.ReviewDto(authorName: "User1")])
            def p1Page2 = createResponse([]) // Stop loop for p1
            def p2Page1 = createResponse([]) // No reviews for p2

        when: "parseReviews is executed"
            reviewsService.parseReviews()

        then: "Pagination for p1 is handled (2 calls to scraper)"
            1 * urlBuilder.buildReviewsApiUrl("http://url1.com", 0) >> "api-p1-0"
            1 * reviewsScraper.fetchReviewPage("api-p1-0") >> p1Page1

            1 * urlBuilder.buildReviewsApiUrl("http://url1.com", 1) >> "api-p1-1"
            1 * reviewsScraper.fetchReviewPage("api-p1-1") >> p1Page2

        and: "Saving logic for p1"
            1 * reviewsMapper.mapToReviews({ it.size() == 1 }) >> [new Review(reviewerName: "User1")]
            1 * reviewRepository.saveAll({ List<Review> list -> list[0].productId == 1L })
            1 * productRepository.save({ it.id == 1L && it.reviewsParsed == true })

        and: "Pagination for p2 is handled (1 call to scraper)"
            1 * urlBuilder.buildReviewsApiUrl("http://url2.com", 0) >> "api-p2-0"
            1 * reviewsScraper.fetchReviewPage("api-p2-0") >> p2Page1
            1 * productRepository.save({ it.id == 2L && it.reviewsParsed == true })
    }

    @Unroll
    def "Should skip product when URL is '#urlScenario'"() {
        given: "A product with a bad URL"
            def product = Product.builder().id(99L).url(urlValue).build()
            productRepository.findAllByReviewsParsedAndRatingIsNotNull(false) >> [product]

        when: "Service runs"
            reviewsService.parseReviews()

        then: "Scraper is never called"
            0 * reviewsScraper.fetchReviewPage(_)
            0 * transactionTemplate.executeWithoutResult(_)

        where:
        urlScenario | urlValue
            "null"      | null
            "empty"     | ""
            "blank"     | "   "
    }

    def "Should continue processing next product if one throws an exception"() {
        given: "Two products"
            def p1 = Product.builder().id(1L).url("url1").build()
            def p2 = Product.builder().id(2L).url("url2").build()
            productRepository.findAllByReviewsParsedAndRatingIsNotNull(false) >> [p1, p2]

        when: "The first product triggers an error"
            reviewsService.parseReviews()

        then: "First product fails"
            1 * urlBuilder.buildReviewsApiUrl("url1", 0) >> { throw new RuntimeException("Network Error") }

        and: "Second product is still processed"
            1 * urlBuilder.buildReviewsApiUrl("url2", 0) >> "api-2"
            1 * reviewsScraper.fetchReviewPage("api-2") >> createResponse([])
            1 * productRepository.save({ it.id == 2L })

        and: "Error is logged"
            assertLog(Level.ERROR, "Error processing reviews for product ID 1: Network Error")
    }

    def "Should handle interrupted exception during sleep"() {
        given: "A product"
            def product = Product.builder().id(1L).url("url").build()
            productRepository.findAllByReviewsParsedAndRatingIsNotNull(false) >> [product]

            // Re-initialize with delay to test interruption
            reviewsService.productLoopDelayMs = 1000

        when: "Thread is interrupted"
            Thread.currentThread().interrupt()
            reviewsService.parseReviews()

        then: "It completes without crashing and handles the flag"
            1 * transactionTemplate.executeWithoutResult(_)
            // Interrupted flag should be set
            Thread.interrupted()
    }

    // Helper to build the nested DTO structure used in your service:
    // response.getReviews().getReviews()
    private ReviewsApiResponseDto createResponse(List<ReviewsApiResponseDto.ReviewDto> items) {
        def wrapper = new ReviewsApiResponseDto.ReviewsWrapper()
        wrapper.setReviews(items)

        def response = new ReviewsApiResponseDto()
        response.setReviews(wrapper)
        return response
    }
}
