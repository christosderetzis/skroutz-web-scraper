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
import spock.lang.Subject

class ReviewsTxServiceSpec extends WithLoggingBaseSpec {

    ReviewsScraper reviewsScraper = Mock()
    ReviewsMapper reviewsMapper = Mock()
    ReviewRepository reviewRepository = Mock()
    ProductRepository productRepository = Mock()

    @Subject
    ReviewsTxService reviewsTxService = new ReviewsTxService(
            reviewsScraper,
            reviewsMapper,
            reviewRepository,
            productRepository
    )

    def "Happy path, process single product with reviews"() {
        given: "a product"
            Product product = Product.builder()
                    .id(1L)
                    .url("http://example.com/product.html")
                    .title("Test Product")
                    .reviewsParsed(false)
                    .build()

        and: "scraped review DTOs"
            def reviewDtos = [
                    new ReviewsApiResponseDto.ReviewDto(
                            id: 1L,
                            rating: 5,
                            authorName: "John Doe",
                            reviewTime: "01/01/2024"
                    ),
                    new ReviewsApiResponseDto.ReviewDto(
                            id: 2L,
                            rating: 4,
                            authorName: "Jane Smith",
                            reviewTime: "02/01/2024"
                    )
            ]

        and: "mapped review entities"
            def reviews = [
                    Review.builder().reviewerName("John Doe").reviewerRating(5).build(),
                    Review.builder().reviewerName("Jane Smith").reviewerRating(4).build()
            ]

        when: "processSingleProduct is called"
            reviewsTxService.processSingleProduct(product)

        then: "reviews are scraped, mapped, and saved"
            1 * reviewsScraper.scrapeReviews(product.url) >> reviewDtos
            1 * reviewsMapper.mapToReviews(reviewDtos) >> reviews
            1 * reviewRepository.saveAll({ List<Review> savedReviews ->
                savedReviews.size() == 2 &&
                        savedReviews.every { it.productId == 1L && it.product == product }
            })
            1 * productRepository.save({ it.reviewsParsed == true })
            0 * _

        and: "success is logged"
            assertLog(Level.INFO, "Fetching and saving reviews for product ID: 1")
            assertLog(Level.INFO, "Saved 2 reviews for product ID: 1")
    }

    def "Happy path, process product with no reviews"() {
        given: "a product"
            Product product = Product.builder()
                    .id(1L)
                    .url("http://example.com/product.html")
                    .title("Test Product")
                    .reviewsParsed(false)
                    .build()

        and: "empty review DTOs"
            def reviewDtos = []

        and: "empty mapped reviews"
            def reviews = []

        when: "processSingleProduct is called"
            reviewsTxService.processSingleProduct(product)

        then: "no reviews are saved but product is marked as parsed"
            1 * reviewsScraper.scrapeReviews(product.url) >> reviewDtos
            1 * reviewsMapper.mapToReviews(reviewDtos) >> reviews
            0 * reviewRepository.saveAll(_)
            1 * productRepository.save({ it.reviewsParsed == true })
            0 * _

        and: "warning is logged"
            assertLog(Level.WARN, "No reviews available for product ID: 1")
    }

    def "Happy path, reviews have productId and product set correctly"() {
        given: "a product with specific ID"
            Product product = Product.builder()
                    .id(42L)
                    .url("http://example.com/product.html")
                    .title("Test Product")
                    .reviewsParsed(false)
                    .build()

        and: "a single review DTO"
            def reviewDtos = [
                    new ReviewsApiResponseDto.ReviewDto(
                            id: 1L,
                            rating: 5,
                            authorName: "Test User"
                    )
            ]

        and: "a mapped review entity"
            def review = Review.builder().reviewerName("Test User").reviewerRating(5).build()

        when: "processSingleProduct is called"
            reviewsTxService.processSingleProduct(product)

        then: "review has correct productId and product reference"
            1 * reviewsScraper.scrapeReviews(product.url) >> reviewDtos
            1 * reviewsMapper.mapToReviews(reviewDtos) >> [review]
            1 * reviewRepository.saveAll({ List<Review> savedReviews ->
                savedReviews[0].productId == 42L &&
                        savedReviews[0].product == product
            })
            1 * productRepository.save(_)
            0 * _
    }

    def "Happy path, product is marked as parsed after processing"() {
        given: "a product that is not yet parsed"
            Product product = Product.builder()
                    .id(1L)
                    .url("http://example.com/product.html")
                    .title("Test Product")
                    .reviewsParsed(false)
                    .build()

        when: "processSingleProduct is called"
            reviewsTxService.processSingleProduct(product)

        then: "product reviewsParsed is set to true and saved"
            1 * reviewsScraper.scrapeReviews(_) >> []
            1 * reviewsMapper.mapToReviews(_) >> []
            1 * productRepository.save({ Product p ->
                p.reviewsParsed == true && p.id == 1L
            })
            0 * _
    }

    def "Happy path, process product with single review"() {
        given: "a product"
            Product product = Product.builder()
                    .id(1L)
                    .url("http://example.com/product.html")
                    .title("Test Product")
                    .reviewsParsed(false)
                    .build()

        and: "single review DTO"
            def reviewDtos = [
                    new ReviewsApiResponseDto.ReviewDto(
                            id: 1L,
                            rating: 3,
                            authorName: "Single User"
                    )
            ]

        and: "mapped review entity"
            def reviews = [
                    Review.builder().reviewerName("Single User").reviewerRating(3).build()
            ]

        when: "processSingleProduct is called"
            reviewsTxService.processSingleProduct(product)

        then: "single review is saved"
            1 * reviewsScraper.scrapeReviews(product.url) >> reviewDtos
            1 * reviewsMapper.mapToReviews(reviewDtos) >> reviews
            1 * reviewRepository.saveAll({ it.size() == 1 })
            1 * productRepository.save(_)
            0 * _

        and: "correct count is logged"
            assertLog(Level.INFO, "Saved 1 reviews for product ID: 1")
    }

    def "Happy path, process product with many reviews"() {
        given: "a product"
            Product product = Product.builder()
                    .id(1L)
                    .url("http://example.com/product.html")
                    .title("Test Product")
                    .reviewsParsed(false)
                    .build()

        and: "many review DTOs"
            def reviewDtos = (1..100).collect { i ->
                new ReviewsApiResponseDto.ReviewDto(
                        id: i.toLong(),
                        rating: (i % 5) + 1,
                        authorName: "User $i"
                )
            }

        and: "mapped review entities"
            def reviews = (1..100).collect { i ->
                Review.builder().reviewerName("User $i").reviewerRating((i % 5) + 1).build()
            }

        when: "processSingleProduct is called"
            reviewsTxService.processSingleProduct(product)

        then: "all reviews are saved"
            1 * reviewsScraper.scrapeReviews(product.url) >> reviewDtos
            1 * reviewsMapper.mapToReviews(reviewDtos) >> reviews
            1 * reviewRepository.saveAll({ it.size() == 100 })
            1 * productRepository.save(_)
            0 * _

        and: "correct count is logged"
            assertLog(Level.INFO, "Saved 100 reviews for product ID: 1")
    }
}
