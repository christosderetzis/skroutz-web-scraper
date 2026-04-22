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
import org.skroutz.scraper.skroutzwebscraper.util.UrlBuilder
import spock.lang.Subject

class ReviewsTxServiceSpec extends WithLoggingBaseSpec {

    ReviewsScraper reviewsScraper = Mock()
    ReviewsMapper reviewsMapper = Mock()
    ReviewRepository reviewRepository = Mock()
    ProductRepository productRepository = Mock()
    UrlBuilder urlBuilder = Mock()

    @Subject
    ReviewsTxService reviewsTxService = new ReviewsTxService(
            reviewsScraper,
            reviewsMapper,
            reviewRepository,
            productRepository,
            urlBuilder,
            0
    )

    def "Happy path, process single product with reviews"() {
        given: "a product"
            Product product = Product.builder()
                    .id(1L)
                    .url("https://example.com/product.html")
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
            1 * urlBuilder.buildReviewsApiUrl("https://example.com/product.html", 0) >> "https://example.com/product/reviews.json?offset=0"
            1 * urlBuilder.buildReviewsApiUrl("https://example.com/product.html", 2) >> "https://example.com/product/reviews.json?offset=2"
            2 * reviewsScraper.fetchReviewPage(_ as String) >>> [
                    new ReviewsApiResponseDto(reviews: new ReviewsApiResponseDto.ReviewsWrapper(reviews: reviewDtos)),
                    new ReviewsApiResponseDto(reviews: new ReviewsApiResponseDto.ReviewsWrapper(reviews: []))
            ]
            1 * reviewsMapper.mapToReviews({ List<ReviewsApiResponseDto.ReviewDto> dtos ->
                dtos.size() == 2 && dtos*.authorName == ["John Doe", "Jane Smith"]
            }) >> reviews
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
                    .url("https://example.com/product.html")
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
            1 * urlBuilder.buildReviewsApiUrl("https://example.com/product.html", 0) >> "https://example.com/product/reviews.json?offset=0"
            1 * reviewsScraper.fetchReviewPage(_ as String) >> new ReviewsApiResponseDto(
                    reviews: new ReviewsApiResponseDto.ReviewsWrapper(reviews: [])
            )
            1 * reviewsMapper.mapToReviews([]) >> reviews
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
                    .url("https://example.com/product.html")
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
            1 * urlBuilder.buildReviewsApiUrl("https://example.com/product.html", 0) >> "https://example.com/product/reviews.json?offset=0"
            1 * urlBuilder.buildReviewsApiUrl("https://example.com/product.html", 1) >> "https://example.com/product/reviews.json?offset=1"
            2 * reviewsScraper.fetchReviewPage(_ as String) >>> [
                    new ReviewsApiResponseDto(reviews: new ReviewsApiResponseDto.ReviewsWrapper(reviews: reviewDtos)),
                    new ReviewsApiResponseDto(reviews: new ReviewsApiResponseDto.ReviewsWrapper(reviews: []))
            ]
            1 * reviewsMapper.mapToReviews({ List<ReviewsApiResponseDto.ReviewDto> dtos ->
                dtos.size() == 1 && dtos[0].authorName == "Test User"
            }) >> [review]
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
                    .url("https://example.com/product.html")
                    .title("Test Product")
                    .reviewsParsed(false)
                    .build()

        when: "processSingleProduct is called"
            reviewsTxService.processSingleProduct(product)

        then: "product reviewsParsed is set to true and saved"
            1 * urlBuilder.buildReviewsApiUrl("https://example.com/product.html", 0) >> "https://example.com/product/reviews.json?offset=0"
            1 * reviewsScraper.fetchReviewPage(_ as String) >> new ReviewsApiResponseDto(
                    reviews: new ReviewsApiResponseDto.ReviewsWrapper(reviews: [])
            )
            1 * reviewsMapper.mapToReviews([]) >> []
            1 * productRepository.save({ Product p ->
                p.reviewsParsed == true && p.id == 1L
            })
            0 * _
    }

    def "Happy path, process product with single review"() {
        given: "a product"
            Product product = Product.builder()
                    .id(1L)
                    .url("https://example.com/product.html")
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
            1 * urlBuilder.buildReviewsApiUrl("https://example.com/product.html", 0) >> "https://example.com/product/reviews.json?offset=0"
            1 * urlBuilder.buildReviewsApiUrl("https://example.com/product.html", 1) >> "https://example.com/product/reviews.json?offset=1"
            2 * reviewsScraper.fetchReviewPage(_ as String) >>> [
                    new ReviewsApiResponseDto(reviews: new ReviewsApiResponseDto.ReviewsWrapper(reviews: reviewDtos)),
                    new ReviewsApiResponseDto(reviews: new ReviewsApiResponseDto.ReviewsWrapper(reviews: []))
            ]
            1 * reviewsMapper.mapToReviews({ List<ReviewsApiResponseDto.ReviewDto> dtos ->
                dtos.size() == 1
            }) >> reviews
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
                    .url("https://example.com/product.html")
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
            1 * urlBuilder.buildReviewsApiUrl("https://example.com/product.html", 0) >> "https://example.com/product/reviews.json?offset=0"
            1 * urlBuilder.buildReviewsApiUrl("https://example.com/product.html", 100) >> "https://example.com/product/reviews.json?offset=100"
            2 * reviewsScraper.fetchReviewPage(_ as String) >>> [
                    new ReviewsApiResponseDto(reviews: new ReviewsApiResponseDto.ReviewsWrapper(reviews: reviewDtos)),
                    new ReviewsApiResponseDto(reviews: new ReviewsApiResponseDto.ReviewsWrapper(reviews: []))
            ]
            1 * reviewsMapper.mapToReviews({ List<ReviewsApiResponseDto.ReviewDto> dtos ->
                dtos.size() == 100
            }) >> reviews
            1 * reviewRepository.saveAll({ it.size() == 100 })
            1 * productRepository.save(_)
            0 * _

        and: "correct count is logged"
            assertLog(Level.INFO, "Saved 100 reviews for product ID: 1")
    }

    def "scrapeReviews handles multiple pages correctly"() {
        given: "a product"
            Product product = Product.builder()
                    .id(1L)
                    .url("https://example.com/product")
                    .reviewsParsed(false)
                    .build()

        and: "first page review DTOs"
            def firstPageDtos = [
                    new ReviewsApiResponseDto.ReviewDto(id: 1L, rating: 5, authorName: "User1"),
                    new ReviewsApiResponseDto.ReviewDto(id: 2L, rating: 4, authorName: "User2")
            ]

        and: "second page review DTOs"
            def secondPageDtos = [
                    new ReviewsApiResponseDto.ReviewDto(id: 3L, rating: 3, authorName: "User3")
            ]

        and: "mapped reviews"
            def allReviews = [
                    Review.builder().reviewerName("User1").reviewerRating(5).build(),
                    Review.builder().reviewerName("User2").reviewerRating(4).build(),
                    Review.builder().reviewerName("User3").reviewerRating(3).build()
            ]

        when: "processSingleProduct is called"
            reviewsTxService.processSingleProduct(product)

        then: "multiple pages are fetched"
            1 * urlBuilder.buildReviewsApiUrl("https://example.com/product", 0) >> "https://example.com/product/reviews.json?offset=0"
            1 * urlBuilder.buildReviewsApiUrl("https://example.com/product", 2) >> "https://example.com/product/reviews.json?offset=2"
            1 * urlBuilder.buildReviewsApiUrl("https://example.com/product", 3) >> "https://example.com/product/reviews.json?offset=3"
            1 * reviewsScraper.fetchReviewPage("https://example.com/product/reviews.json?offset=0") >>
                    new ReviewsApiResponseDto(reviews: new ReviewsApiResponseDto.ReviewsWrapper(reviews: firstPageDtos))
            1 * reviewsScraper.fetchReviewPage("https://example.com/product/reviews.json?offset=2") >>
                    new ReviewsApiResponseDto(reviews: new ReviewsApiResponseDto.ReviewsWrapper(reviews: secondPageDtos))
            1 * reviewsScraper.fetchReviewPage("https://example.com/product/reviews.json?offset=3") >>
                    new ReviewsApiResponseDto(reviews: new ReviewsApiResponseDto.ReviewsWrapper(reviews: []))

        and: "all reviews are mapped and saved"
            1 * reviewsMapper.mapToReviews({ List<ReviewsApiResponseDto.ReviewDto> dtos ->
                dtos.size() == 3 && dtos*.authorName == ["User1", "User2", "User3"]
            }) >> allReviews
            1 * reviewRepository.saveAll({ it.size() == 3 })
            1 * productRepository.save(_)
            0 * _

        and: "logs indicate total count"
            assertLog(Level.INFO, "Scraping reviews for")
            assertLog(Level.INFO, "Total reviews fetched: 3")
    }

    def "scrapeReviews handles product URL with .html extension"() {
        given: "a product with .html in URL"
            Product product = Product.builder()
                    .id(1L)
                    .url("https://example.com/product.html")
                    .reviewsParsed(false)
                    .build()

        when: "processSingleProduct is called"
            reviewsTxService.processSingleProduct(product)

        then: "URL is transformed correctly (without .html)"
            1 * urlBuilder.buildReviewsApiUrl("https://example.com/product.html", 0) >> "https://example.com/product/reviews.json?offset=0"
            1 * reviewsScraper.fetchReviewPage("https://example.com/product/reviews.json?offset=0") >>
                    new ReviewsApiResponseDto(reviews: new ReviewsApiResponseDto.ReviewsWrapper(reviews: []))
            1 * reviewsMapper.mapToReviews([]) >> []
            1 * productRepository.save(_)
            0 * _
    }

    def "scrapeReviews accumulates offset correctly across pages"() {
        given: "a product"
            Product product = Product.builder()
                    .id(1L)
                    .url("https://example.com/product")
                    .reviewsParsed(false)
                    .build()

        and: "varying page sizes"
            def page1 = [
                    new ReviewsApiResponseDto.ReviewDto(id: 1L, rating: 5, authorName: "User1"),
                    new ReviewsApiResponseDto.ReviewDto(id: 2L, rating: 4, authorName: "User2"),
                    new ReviewsApiResponseDto.ReviewDto(id: 3L, rating: 3, authorName: "User3")
            ]
            def page2 = [
                    new ReviewsApiResponseDto.ReviewDto(id: 4L, rating: 2, authorName: "User4"),
                    new ReviewsApiResponseDto.ReviewDto(id: 5L, rating: 1, authorName: "User5")
            ]

        and: "mapped reviews"
            def allReviews = (1..5).collect { Review.builder().build() }

        when: "processSingleProduct is called"
            reviewsTxService.processSingleProduct(product)

        then: "correct offsets are used"
            1 * urlBuilder.buildReviewsApiUrl("https://example.com/product", 0) >> "https://example.com/product/reviews.json?offset=0"
            1 * urlBuilder.buildReviewsApiUrl("https://example.com/product", 3) >> "https://example.com/product/reviews.json?offset=3"
            1 * urlBuilder.buildReviewsApiUrl("https://example.com/product", 5) >> "https://example.com/product/reviews.json?offset=5"
            1 * reviewsScraper.fetchReviewPage("https://example.com/product/reviews.json?offset=0") >>
                    new ReviewsApiResponseDto(reviews: new ReviewsApiResponseDto.ReviewsWrapper(reviews: page1))
            1 * reviewsScraper.fetchReviewPage("https://example.com/product/reviews.json?offset=3") >>
                    new ReviewsApiResponseDto(reviews: new ReviewsApiResponseDto.ReviewsWrapper(reviews: page2))
            1 * reviewsScraper.fetchReviewPage("https://example.com/product/reviews.json?offset=5") >>
                    new ReviewsApiResponseDto(reviews: new ReviewsApiResponseDto.ReviewsWrapper(reviews: []))

        and: "all reviews are saved"
            1 * reviewsMapper.mapToReviews({ List<ReviewsApiResponseDto.ReviewDto> dtos ->
                dtos.size() == 5
            }) >> allReviews
            1 * reviewRepository.saveAll({ it.size() == 5 })
            1 * productRepository.save(_)
            0 * _
    }
}
