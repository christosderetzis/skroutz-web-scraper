package org.skroutz.scraper.skroutzwebscraper.scraping.application.service.processing

import org.skroutz.scraper.skroutzwebscraper.scraping.infrastructure.scraper.ReviewsScraper
import org.skroutz.scraper.skroutzwebscraper.scraping.application.service.processing.ReviewsScraperService
import org.skroutz.scraper.skroutzwebscraper.scraping.infrastructure.utils.UrlBuilder
import spock.lang.Specification
import spock.lang.Subject

class ReviewsScraperServiceSpec extends Specification {

    ReviewsScraper reviewsScraper = Mock(ReviewsScraper)
    UrlBuilder urlBuilder = Mock(UrlBuilder)

    @Subject
    ReviewsScraperService service

    void setup() {
        service = new ReviewsScraperService(reviewsScraper, urlBuilder)
        service.reviewPageDelayMs = 0
    }

    def "should restore interrupted status when InterruptedException occurs during sleep"() {
        given: "A product ID and URL"
            def productId = 123L
            def productUrl = "http://example.com/product"

            // Re-initialize with delay to test interruption
            service.reviewPageDelayMs = 1000

        and: "We force the current thread to interrupt when urlBuilder is called"
            Thread.currentThread().interrupt()

        when: "The product reviews are scraped"
            service.scrapeProductReviews(productId, productUrl)

        then: "The thread's interrupted status was successfully restored by your catch block"
            Thread.interrupted() == true
    }
}
