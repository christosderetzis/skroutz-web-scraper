package org.skroutz.scraper.skroutzwebscraper.service

import ch.qos.logback.classic.Level
import org.skroutz.scraper.skroutzwebscraper.base.WithLoggingBaseSpec
import org.skroutz.scraper.skroutzwebscraper.entity.Product
import org.skroutz.scraper.skroutzwebscraper.entity.Review
import org.skroutz.scraper.skroutzwebscraper.repository.ProductRepository
import org.skroutz.scraper.skroutzwebscraper.repository.ReviewRepository
import org.skroutz.scraper.skroutzwebscraper.scraper.ReviewsScraper
import spock.lang.Subject

class ReviewsServiceSpec extends WithLoggingBaseSpec {

    ProductRepository productRepository = Mock()
    ReviewRepository reviewRepository = Mock()
    ReviewsScraper reviewsScraper = Mock()

    @Subject
    ReviewsService reviewsService = new ReviewsService(productRepository, reviewRepository, reviewsScraper)

    def "Happy path, parse reviews successfully"() {
        given: "product id and page number"
            Product product = new Product(id: 1L, url: "http://example.com/product", reviewsParsed: false, title: "Sample Product")

        and: "2 reviews to be scraped"
            def reviews = [
                    new Review(id: 1L, reviewerName: "John Doe", reviewerRating: 5),
                    new Review(id: 2L, reviewerName: "Jane Smith", reviewerRating: 4)
            ]

        when: "scrapeReviews is called"
            reviewsService.parseReviews()

        then: "product is fetched from repository"
            1 * productRepository.findAllByReviewsParsed(false) >> [product]
            1 * reviewsScraper.scrapeReviews(product.url) >> reviews
            1 * productRepository.save({ it.reviewsParsed == true })
            1 * reviewRepository.saveAll({ it.size() == 2 && it[0].reviewerName == "John Doe" && it[1].reviewerName == "Jane Smith" })
            0 * _

        and: "Logging messages are printed"
            assertLog(Level.INFO, "Successfully parsed reviews for product: Sample Product")
    }

    def "Happy path, empty product url"() {
        given: "product with empty URL"
            Product product = new Product(id: 1L, url: "", reviewsParsed: false, title: "Sample Product")

        when: "scrapeReviews is called"
            reviewsService.parseReviews()

        then: "product is fetched from repository"
            1 * productRepository.findAllByReviewsParsed(false) >> [product]
            0 * _

        and: "Logging messages are printed"
            assertLog(Level.WARN, "Product URL is empty or null for product: 1")
    }

    def "Unhappy path, exception during scraping"() {
        given: "product id and page number"
            Product product = new Product(id: 1L, url: "http://example.com/product", reviewsParsed: false, title: "Sample Product")

        when: "scrapeReviews is called"
            reviewsService.parseReviews()

        then: "product is fetched from repository"
            1 * productRepository.findAllByReviewsParsed(false) >> [product]
            1 * reviewsScraper.scrapeReviews(product.url) >> { throw new RuntimeException("Scraping error") }
            0 * _

        and: "Logging messages are printed"
            assertLog(Level.ERROR, "Error parsing reviews for product 1")
    }
}
