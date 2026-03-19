package org.skroutz.scraper.skroutzwebscraper.service

import ch.qos.logback.classic.Level
import org.skroutz.scraper.skroutzwebscraper.base.WithLoggingBaseSpec
import org.skroutz.scraper.skroutzwebscraper.entity.Product
import org.skroutz.scraper.skroutzwebscraper.repository.ProductRepository
import spock.lang.Subject
import spock.lang.Unroll

class ReviewsServiceSpec extends WithLoggingBaseSpec {

    ProductRepository productRepository = Mock()
    ReviewsTxService reviewsTxService = Mock()

    @Subject
    ReviewsService reviewsService = new ReviewsService(reviewsTxService, productRepository)

    def setup() {
        reviewsService.@delayMs = 0L
    }

    def "Happy path, parse reviews successfully for single product"() {
        given: "a product with valid URL"
            Product product = Product.builder()
                    .id(1L)
                    .url("http://example.com/product.html")
                    .reviewsParsed(false)
                    .title("Sample Product")
                    .build()

        when: "parseReviews is called"
            reviewsService.parseReviews()

        then: "product is fetched and processed via ReviewsTxService"
            1 * productRepository.findAllByReviewsParsedAndRatingIsNotNull(false) >> [product]
            1 * reviewsTxService.processSingleProduct(product)
            0 * _
    }

    def "Happy path, parse reviews for multiple products"() {
        given: "multiple products with valid URLs"
            Product product1 = Product.builder()
                    .id(1L)
                    .url("http://example.com/product1.html")
                    .reviewsParsed(false)
                    .title("Product 1")
                    .build()
            Product product2 = Product.builder()
                    .id(2L)
                    .url("http://example.com/product2.html")
                    .reviewsParsed(false)
                    .title("Product 2")
                    .build()

        when: "parseReviews is called"
            reviewsService.parseReviews()

        then: "both products are processed via ReviewsTxService"
            1 * productRepository.findAllByReviewsParsedAndRatingIsNotNull(false) >> [product1, product2]
            1 * reviewsTxService.processSingleProduct(product1)
            1 * reviewsTxService.processSingleProduct(product2)
            0 * _
    }

    @Unroll
    def "Skip product with #scenario URL"() {
        given: "a product with invalid URL"
            Product product = Product.builder()
                    .id(1L)
                    .url(url)
                    .reviewsParsed(false)
                    .title("Sample Product")
                    .build()

        when: "parseReviews is called"
            reviewsService.parseReviews()

        then: "product is fetched but not processed"
            1 * productRepository.findAllByReviewsParsedAndRatingIsNotNull(false) >> [product]
            0 * reviewsTxService.processSingleProduct(_)
            0 * _

        and: "warning is logged"
            assertLog(Level.WARN, "Product URL is empty or null for product ID: 1")

        where:
            scenario     | url
            "empty"      | ""
            "null"       | null
            "blank"      | "   "
    }

    def "Continue processing other products when one fails"() {
        given: "multiple products"
            Product product1 = Product.builder()
                    .id(1L)
                    .url("http://example.com/product1.html")
                    .reviewsParsed(false)
                    .title("Product 1")
                    .build()
            Product product2 = Product.builder()
                    .id(2L)
                    .url("http://example.com/product2.html")
                    .reviewsParsed(false)
                    .title("Product 2")
                    .build()

        when: "parseReviews is called"
            reviewsService.parseReviews()

        then: "first product fails but second product is still processed"
            1 * productRepository.findAllByReviewsParsedAndRatingIsNotNull(false) >> [product1, product2]
            1 * reviewsTxService.processSingleProduct(product1) >> { throw new RuntimeException("Scraping error") }
            1 * reviewsTxService.processSingleProduct(product2)
            0 * _

        and: "error is logged for failed product"
            assertLog(Level.ERROR, "Error processing reviews for product ID 1")
    }

    def "Handle exception during single product processing"() {
        given: "a product with valid URL"
            Product product = Product.builder()
                    .id(1L)
                    .url("http://example.com/product.html")
                    .reviewsParsed(false)
                    .title("Sample Product")
                    .build()

        when: "parseReviews is called"
            reviewsService.parseReviews()

        then: "exception is caught and logged"
            1 * productRepository.findAllByReviewsParsedAndRatingIsNotNull(false) >> [product]
            1 * reviewsTxService.processSingleProduct(product) >> { throw new InterruptedException("Interrupted") }
            0 * _

        and: "error is logged"
            assertLog(Level.ERROR, "Error processing reviews for product ID 1")
    }

    def "No products to process"() {
        when: "parseReviews is called with no products"
            reviewsService.parseReviews()

        then: "empty list is returned from repository"
            1 * productRepository.findAllByReviewsParsedAndRatingIsNotNull(false) >> []
            0 * reviewsTxService.processSingleProduct(_)
            0 * _
    }

    def "Mixed products - some with valid URLs, some without"() {
        given: "products with mixed URL validity"
            Product validProduct = Product.builder()
                    .id(1L)
                    .url("http://example.com/product.html")
                    .reviewsParsed(false)
                    .title("Valid Product")
                    .build()
            Product emptyUrlProduct = Product.builder()
                    .id(2L)
                    .url("")
                    .reviewsParsed(false)
                    .title("Empty URL Product")
                    .build()
            Product anotherValidProduct = Product.builder()
                    .id(3L)
                    .url("http://example.com/product2.html")
                    .reviewsParsed(false)
                    .title("Another Valid Product")
                    .build()

        when: "parseReviews is called"
            reviewsService.parseReviews()

        then: "only valid products are processed"
            1 * productRepository.findAllByReviewsParsedAndRatingIsNotNull(false) >> [validProduct, emptyUrlProduct, anotherValidProduct]
            1 * reviewsTxService.processSingleProduct(validProduct)
            1 * reviewsTxService.processSingleProduct(anotherValidProduct)
            0 * _

        and: "warning is logged for invalid product"
            assertLog(Level.WARN, "Product URL is empty or null for product ID: 2")
    }
}
