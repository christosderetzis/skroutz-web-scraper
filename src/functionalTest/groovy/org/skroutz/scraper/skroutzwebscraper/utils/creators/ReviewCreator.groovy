package org.skroutz.scraper.skroutzwebscraper.utils.creators

import org.skroutz.scraper.skroutzwebscraper.review.domain.entity.Review

import java.time.LocalDate

class ReviewCreator {

    static Review createReview(Long productId, String reviewerName, Integer reviewerRating,
                               LocalDate reviewDate, Integer helpfulVotes, Integer totalVotes,
                               String reviewText) {
        Review.builder()
                .productId(productId)
                .reviewerName(reviewerName)
                .reviewerRating(reviewerRating)
                .reviewDate(reviewDate)
                .helpfulVotes(helpfulVotes)
                .totalVotes(totalVotes)
                .reviewText(reviewText)
                .build()
    }
}


