package org.skroutz.scraper.skroutzwebscraper.review

import org.mockito.MockedStatic
import org.mockito.Mockito
import org.skroutz.scraper.skroutzwebscraper.product.ProductDto
import org.skroutz.scraper.skroutzwebscraper.product.ProductFacade
import org.skroutz.scraper.skroutzwebscraper.review.application.service.ReviewsSummarizationService
import org.skroutz.scraper.skroutzwebscraper.review.domain.chunker.ReviewChunker
import org.skroutz.scraper.skroutzwebscraper.review.domain.entity.ReviewSummary
import org.skroutz.scraper.skroutzwebscraper.review.domain.repository.ReviewRepository
import org.skroutz.scraper.skroutzwebscraper.review.domain.repository.ReviewSummaryRepository
import org.skroutz.scraper.skroutzwebscraper.review.infrastructure.agent.ReviewSummarizer
import org.skroutz.scraper.skroutzwebscraper.review.infrastructure.dto.ReviewSummaryDto
import org.skroutz.scraper.skroutzwebscraper.review.infrastructure.mapper.ReviewSummaryMapper
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import spock.lang.Specification

class ReviewsSummarizationServiceSpec extends Specification {

    // Mocks
    ProductFacade productFacade = Mock()
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
                CHUNK_SIZE, productFacade, reviewRepository,
                reviewSummarizationRepository, reviewSummarizer, reviewSummaryMapper
        )
        // Mocking static method using Mockito within Spock lifecycle
        mockedChunker = Mockito.mockStatic(ReviewChunker.class)
    }

    def cleanup() {
        mockedChunker.close()
    }

    def "summarizeReviews returns cached DTO when summary already exists"() {
        given: "product exists with reviewsParsed true and summary already exists in repository"
            def product = new ProductDto(1L, "http://example.com/product1", "iphone 18", "256gb ram", "MOBILE_PHONES")
            def existingSummary = new ReviewSummary()
            def expectedDto = new ReviewSummaryDto("Summary", ["P"], ["C"], "Pos")

            productFacade.getProductById(PRODUCT_ID) >> product
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
            def product = new ProductDto(1L, "http://example.com/product1", "iphone 18", "256gb ram", "MOBILE_PHONES")
            productFacade.getProductById(PRODUCT_ID) >> product
            reviewSummarizationRepository.findByProductId(PRODUCT_ID) >> Optional.empty()
            reviewRepository.findAllByProductIdAndReviewTextIsNotNull(PRODUCT_ID) >> []

        when: "summarizeReviews is called for product with no valid reviews"
            service.summarizeReviews(PRODUCT_ID)

        then: "ResponseStatusException with BAD_REQUEST is thrown indicating no reviews to summarize"
            def e = thrown(ResponseStatusException)
            e.statusCode == HttpStatus.BAD_REQUEST
    }
}
