package org.skroutz.scraper.skroutzwebscraper.processing.listener

import org.skroutz.scraper.skroutzwebscraper.processing.entity.Product
import org.skroutz.scraper.skroutzwebscraper.processing.entity.Review
import org.skroutz.scraper.skroutzwebscraper.processing.mapper.ReviewsMapper
import org.skroutz.scraper.skroutzwebscraper.processing.repository.ProductRepository
import org.skroutz.scraper.skroutzwebscraper.processing.repository.ReviewRepository
import org.skroutz.scraper.skroutzwebscraper.scraping.dto.ReviewsApiResponseDto
import org.skroutz.scraper.skroutzwebscraper.scraping.event.ReviewsScrapedEvent
import spock.lang.Specification
import spock.lang.Subject

class ReviewsEventListenerSpec extends Specification {

    ReviewsMapper reviewsMapper = Mock()
    ReviewRepository reviewRepository = Mock()
    ProductRepository productRepository = Mock()

    @Subject
    ReviewsEventListener listener = new ReviewsEventListener(reviewsMapper, reviewRepository, productRepository)

    def "Happy path - maps and saves reviews, marks product as parsed"() {
        given: "a product and scraped reviews"
            def product = Product.builder()
                    .id(1L)
                    .title("Test Product")
                    .url("http://example.com/product")
                    .reviewsParsed(false)
                    .build()

            def reviewDto1 = new ReviewsApiResponseDto.ReviewDto()
            reviewDto1.setAuthorName("Reviewer 1")
            reviewDto1.setRating(5)

            def reviewDto2 = new ReviewsApiResponseDto.ReviewDto()
            reviewDto2.setAuthorName("Reviewer 2")
            reviewDto2.setRating(3)

            def reviewDtos = [reviewDto1, reviewDto2]
            def event = new ReviewsScrapedEvent(1L, reviewDtos)

            def review1 = Review.builder().reviewerName("Reviewer 1").reviewerRating(5).build()
            def review2 = Review.builder().reviewerName("Reviewer 2").reviewerRating(3).build()

        when: "the event is handled"
            listener.handleReviewsScraped(event)

        then: "product is looked up"
            1 * productRepository.findById(1L) >> Optional.of(product)

        and: "reviews are mapped"
            1 * reviewsMapper.mapToReviews(reviewDtos) >> [review1, review2]

        and: "reviews have productId and product set and are saved"
            1 * reviewRepository.saveAll({ List<Review> reviews ->
                reviews.size() == 2 &&
                reviews.every { it.productId == 1L && it.product == product }
            })

        and: "product is marked as reviews parsed and saved"
            1 * productRepository.save({ Product p ->
                p.id == 1L && p.reviewsParsed == true
            })
    }

    def "Empty reviews list - still marks product as parsed"() {
        given: "a product and an empty reviews list"
            def product = Product.builder()
                    .id(1L)
                    .title("Test Product")
                    .url("http://example.com/product")
                    .reviewsParsed(false)
                    .build()

            def event = new ReviewsScrapedEvent(1L, [])

        when: "the event is handled"
            listener.handleReviewsScraped(event)

        then: "product is looked up"
            1 * productRepository.findById(1L) >> Optional.of(product)

        and: "mapper returns empty list"
            1 * reviewsMapper.mapToReviews([]) >> []

        and: "no reviews are saved"
            0 * reviewRepository.saveAll(_)

        and: "product is still marked as reviews parsed and saved"
            1 * productRepository.save({ Product p ->
                p.id == 1L && p.reviewsParsed == true
            })
    }

    def "Product not found - throws IllegalStateException"() {
        given: "an event for a non-existent product"
            def event = new ReviewsScrapedEvent(999L, [])

        when: "the event is handled"
            listener.handleReviewsScraped(event)

        then: "product is not found"
            1 * productRepository.findById(999L) >> Optional.empty()

        and: "an IllegalStateException is thrown"
            def ex = thrown(IllegalStateException)
            ex.message.contains("Product not found: 999")

        and: "no reviews are mapped or saved"
            0 * reviewsMapper.mapToReviews(_)
            0 * reviewRepository.saveAll(_)
            0 * productRepository.save(_)
    }
}
