package org.skroutz.scraper.skroutzwebscraper.specs

import org.skroutz.scraper.skroutzwebscraper.entity.Product
import org.skroutz.scraper.skroutzwebscraper.entity.Review
import org.skroutz.scraper.skroutzwebscraper.scheduled.ReviewsScheduler
import org.skroutz.scraper.skroutzwebscraper.utils.base.BaseFunctionalSpec
import org.skroutz.scraper.skroutzwebscraper.utils.creators.ProductCreator
import org.springframework.beans.factory.annotation.Autowired


class ScrapeReviewsFunctionalSpec extends BaseFunctionalSpec {

    @Autowired
    ReviewsScheduler reviewsScheduler

    def "Scrape reviews, happy path"() {
        given:
            def maxReviews = 10
            def expectedInitialReviews = 3  // Mock HTML initially loads 3 reviews
            Product product = ProductCreator.createRandomProduct().tap {
                url = "http://mockserver/reviews-page.html?maxReviews=${maxReviews}"
            }
            productRepository.save(product)

        when:
            reviewsScheduler.parseReviews()

        then: "Product reviewsParsed is true"
            Product savedProduct = productRepository.findAll().getFirst()
            with(savedProduct) {
                reviewsParsed == true
            }

        and: "Only initially visible reviews are scraped (no button clicking)"
            List<Review> reviews = reviewRepository.findAll()
            assert reviews.size() == expectedInitialReviews
            assert reviews.every { it.productId == savedProduct.id }
    }

    def "Scrape reviews, blank url"() {
        given:
            Product product = ProductCreator.createRandomProduct().tap {
                url = ""
            }
            productRepository.save(product)

        when:
            reviewsScheduler.parseReviews()

        then:
            List<Review> reviews = reviewRepository.findAll()
            assert reviews.size() == 0
    }
}
