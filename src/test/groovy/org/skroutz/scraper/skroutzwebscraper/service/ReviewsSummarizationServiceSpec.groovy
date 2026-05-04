package org.skroutz.scraper.skroutzwebscraper.service

import org.mockito.MockedStatic
import org.mockito.Mockito
import org.skroutz.scraper.skroutzwebscraper.agent.ReviewSummarizer
import org.skroutz.scraper.skroutzwebscraper.dto.ReviewSummaryDto
import org.skroutz.scraper.skroutzwebscraper.entity.Product
import org.skroutz.scraper.skroutzwebscraper.entity.Review
import org.skroutz.scraper.skroutzwebscraper.entity.ReviewSummary
import org.skroutz.scraper.skroutzwebscraper.exception.ProductNotFoundException
import org.skroutz.scraper.skroutzwebscraper.mapper.ReviewSummaryMapper
import org.skroutz.scraper.skroutzwebscraper.repository.ProductRepository
import org.skroutz.scraper.skroutzwebscraper.repository.ReviewRepository
import org.skroutz.scraper.skroutzwebscraper.repository.ReviewSummaryRepository
import org.skroutz.scraper.skroutzwebscraper.utils.ReviewChunker
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import spock.lang.Specification

import static org.mockito.ArgumentMatchers.*

class ReviewsSummarizationServiceSpec extends Specification {

    // Mocks
    ProductRepository productRepository = Mock()
    ReviewRepository reviewRepository = Mock()
    ReviewSummaryRepository reviewSummarizationRepository = Mock()
    ReviewSummarizer reviewSummarizer = Mock()
    ReviewSummaryMapper reviewSummaryMapper = Mock()

    // System Under Test
    ReviewsSummarizationService service

    // Static Mocking helper
    MockedStatic<ReviewChunker> mockedChunker

    final int CHUNK_SIZE = 100
    final Long PRODUCT_ID = 1L

    def setup() {
        service = new ReviewsSummarizationService(
                CHUNK_SIZE, productRepository, reviewRepository,
                reviewSummarizationRepository, reviewSummarizer, reviewSummaryMapper
        )
        // Mocking static method using Mockito within Spock lifecycle
        mockedChunker = Mockito.mockStatic(ReviewChunker.class)
    }

    def cleanup() {
        mockedChunker.close()
    }

    def "summarizeReviews throws ProductNotFoundException when product is not found"() {
        given: "productRepository returns empty for given product ID"
            productRepository.findById(PRODUCT_ID) >> Optional.empty()

        when: "summarizeReviews is called with non-existent product ID"
            service.summarizeReviews(PRODUCT_ID)

        then: "ProductNotFoundException is thrown"
            thrown(ProductNotFoundException)
    }

    def "summarizeReviews throws BadRequest when reviews have not been parsed"() {
        given: "product exists but reviewsParsed is false"
            def product = new Product(reviewsParsed: false)
            productRepository.findById(PRODUCT_ID) >> Optional.of(product)

        when: "summarizeReviews is called for product with unparsed reviews"
            service.summarizeReviews(PRODUCT_ID)

        then: "ResponseStatusException with BAD_REQUEST is thrown"
            def e = thrown(ResponseStatusException)
            e.statusCode == HttpStatus.BAD_REQUEST
            e.reason.contains("Cannot summarize reviews for product ID 1")
    }

    def "summarizeReviews returns cached DTO when summary already exists"() {
        given: "product exists with reviewsParsed true and summary already exists in repository"
            def product = new Product(reviewsParsed: true)
            def existingSummary = new ReviewSummary()
            def expectedDto = new ReviewSummaryDto("Summary", ["P"], ["C"], "Pos")

            productRepository.findById(PRODUCT_ID) >> Optional.of(product)
            reviewSummarizationRepository.findByProductId(PRODUCT_ID) >> Optional.of(existingSummary)
            reviewSummaryMapper.toDto(existingSummary) >> expectedDto

        when: "summarizeReviews is called for product with existing summary"
            def result = service.summarizeReviews(PRODUCT_ID)

        then: "cached summary DTO is returned without querying reviews"
            result == expectedDto
            0 * reviewRepository.findAllByProductIdAndReviewTextIsNotNull(_)
    }

    def "summarizeReviews throws BadRequest when no reviews with text are found"() {
        given: "product exists with reviewsParsed true but no reviews with non-null text are found"
            def product = new Product(reviewsParsed: true)
            productRepository.findById(PRODUCT_ID) >> Optional.of(product)
            reviewSummarizationRepository.findByProductId(PRODUCT_ID) >> Optional.empty()
            reviewRepository.findAllByProductIdAndReviewTextIsNotNull(PRODUCT_ID) >> []

        when: "summarizeReviews is called for product with no valid reviews"
            service.summarizeReviews(PRODUCT_ID)

        then: "ResponseStatusException with BAD_REQUEST is thrown indicating no reviews to summarize"
            def e = thrown(ResponseStatusException)
            e.statusCode == HttpStatus.BAD_REQUEST
    }

    def "summarizeReviews filters out reviews with null or blank text"() {
        given:
        def product = new Product(reviewsParsed: true)
        def r1 = new Review(reviewText: "Valid")
        def r2 = new Review(reviewText: null)
        def r3 = new Review(reviewText: "   ")

        productRepository.findById(PRODUCT_ID) >> Optional.of(product)
        reviewSummarizationRepository.findByProductId(PRODUCT_ID) >> Optional.empty()
        reviewRepository.findAllByProductIdAndReviewTextIsNotNull(PRODUCT_ID) >> [r1, r2, r3]

        mockedChunker.when(() -> ReviewChunker.chunkByCharSize(anyList(), eq(CHUNK_SIZE)))
                .thenReturn(["Valid"])

        reviewSummarizer.summarizeChunk(_, _, _) >> new ReviewSummaryDto("S", null, null, "S")

        when:
        service.summarizeReviews(PRODUCT_ID)

        then:
        // We use the mockedChunker's verify because it's a Mockito object, not a Spock mock
        mockedChunker.verify(() -> ReviewChunker.chunkByCharSize(argThat { list -> list.size() == 1 }, eq(CHUNK_SIZE)))
    }

    private static Review review(String text) {
        new Review(
                reviewText: text,
                reviewerRating: 5,
                isVerifiedPurchase: true,
                helpfulVotes: 1,
                totalVotes: 2,
                pros: ["good"],
                cons: ["bad"],
                neutral: ["ok"]
        )
    }
}
