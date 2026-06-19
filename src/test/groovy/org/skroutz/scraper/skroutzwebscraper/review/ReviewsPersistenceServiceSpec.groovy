package org.skroutz.scraper.skroutzwebscraper.review


import org.skroutz.scraper.skroutzwebscraper.product.domain.entity.Product
import org.skroutz.scraper.skroutzwebscraper.product.domain.repository.ProductRepository
import org.skroutz.scraper.skroutzwebscraper.review.application.service.ReviewsPersistenceService
import org.skroutz.scraper.skroutzwebscraper.review.domain.repository.ReviewRepository
import org.skroutz.scraper.skroutzwebscraper.review.infrastructure.mapper.ReviewsMapper
import org.skroutz.scraper.skroutzwebscraper.scraping.infrastructure.dto.events.ReviewsScrapeResult
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class ReviewsPersistenceServiceSpec extends Specification {

    // Mocks for dependencies
    ReviewRepository reviewRepository = Mock(ReviewRepository)
    ProductRepository productRepository = Mock(ProductRepository)
    ReviewsMapper reviewsMapper = Mock(ReviewsMapper)

    @Subject
    ReviewsPersistenceService service

    void setup() {
        service = new ReviewsPersistenceService(reviewRepository, productRepository, reviewsMapper)
    }

    def "should throw IllegalArgumentException when product ID does not exist in repository"() {
        given: "A scrape result with an invalid product ID"
            def nonExistentProductId = 999L
            def scrapeResult = new ReviewsScrapeResult(nonExistentProductId, [], true)

        and: "The repository returns an empty Optional for this product ID"
            productRepository.findById(nonExistentProductId) >> Optional.empty()

        when: "We attempt to save the reviews result"
            service.saveReviewsResult(scrapeResult)

        then: "An IllegalArgumentException is thrown with the expected message"
            def exception = thrown(IllegalArgumentException)
            exception.message == "Product target entity missing ID: " + nonExistentProductId

        and: "No subsequent persistence or mapping actions take place"
            0 * reviewsMapper.mapToReviews(_)
            0 * reviewRepository.saveAll(_)
            0 * productRepository.save(_)
    }

    @Unroll
    def "should skip saving reviews but still update product when reviews list is #description"() {
        given: "A valid product and a successful scrape result"
            def productId = 123L
            def product = new Product(id: productId, reviewsParsed: false)
            def scrapeResult = new ReviewsScrapeResult(productId, [], true)

            productRepository.findById(productId) >> Optional.of(product)
            reviewsMapper.mapToReviews(scrapeResult.reviewDtos()) >> mockReviewsResponse

        when: "The service saves the scrape result"
            service.saveReviewsResult(scrapeResult)

        then: "No reviews are processed or saved"
            0 * reviewRepository.saveAll(_)

        and: "The product is still marked as parsed and saved"
            1 * productRepository.save(product)
            product.reviewsParsed

        where:
            description | mockReviewsResponse
            "empty"     | []
            "null"      | null
    }
}
