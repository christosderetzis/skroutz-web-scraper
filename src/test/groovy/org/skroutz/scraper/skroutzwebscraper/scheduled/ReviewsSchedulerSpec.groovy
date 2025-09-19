package org.skroutz.scraper.skroutzwebscraper.scheduled

import org.skroutz.scraper.skroutzwebscraper.service.ReviewsService
import spock.lang.Specification

class ReviewsSchedulerSpec extends Specification {

    ReviewsScheduler reviewsScheduler
    ReviewsService reviewsService
    def setup() {
        reviewsService = Mock(ReviewsService)
        reviewsScheduler = new ReviewsScheduler(reviewsService)
    }

    def "should call reviewsService.scrapeReviews()"() {
        when:
            reviewsScheduler.parseReviews()

        then:
            1 * reviewsService.parseReviews()
            0 * _
    }
}
