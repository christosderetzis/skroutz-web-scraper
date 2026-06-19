package org.skroutz.scraper.skroutzwebscraper.review

import ch.qos.logback.classic.Level
import org.skroutz.scraper.skroutzwebscraper.base.WithLoggingBaseSpec
import org.skroutz.scraper.skroutzwebscraper.review.domain.entity.Review
import org.skroutz.scraper.skroutzwebscraper.review.infrastructure.mapper.ReviewsMapper
import org.skroutz.scraper.skroutzwebscraper.scraping.infrastructure.dto.api.ReviewsApiResponseDto
import spock.lang.Subject
import spock.lang.Unroll

import java.time.LocalDate

class ReviewsMapperSpec extends WithLoggingBaseSpec {

    @Subject
    ReviewsMapper reviewsMapper = new ReviewsMapper()

    def "mapToReviews with multiple reviews"() {
        given: "multiple review DTOs"
            def reviewDtos = (1..9).collect { i ->
                new ReviewsApiResponseDto.ReviewDto(
                        id: i.toLong(),
                        rating: (i % 5) + 1,
                        authorName: "User $i",
                        reviewTime: "01/0$i/2024".take(10)
                )
            }

        when: "mapping to reviews"
            List<Review> reviews = reviewsMapper.mapToReviews(reviewDtos)

        then: "all reviews are mapped"
            reviews.size() == 9
            with(reviews) {
                it*.reviewerName == (1..9).collect { "User $it" }
                it*.reviewerRating == (1..9).collect { (it % 5) + 1 }
                it*.reviewDate == (1..9).collect { LocalDate.of(2024, it, 01) }
            }
    }

    def "mapToReviews maps all fields correctly"() {
        given: "a review DTO with all fields"
            def reviewDto = new ReviewsApiResponseDto.ReviewDto(
                    id: 123L,
                    rating: 4,
                    authorName: "Complete Reviewer",
                    reviewTime: "25/12/2023",
                    helpfulnessMessage: "5 out of 10 found this review helpful",
                    originalFormattedReview: "<p>This is the <strong>review text</strong> with HTML.</p>",
                    helpfulVotesCount: 5,
                    aggregatedReviewData: '''
                        <ul class="pros"><li>Pro 1</li><li>Pro 2</li></ul>
                        <ul class="cons"><li>Con 1</li></ul>
                        <ul class="so-so"><li>Neutral 1</li><li>Neutral 2</li><li>Neutral 3</li></ul>
                    ''',
                    verified: true
            )

        when: "mapping to review"
            List<Review> reviews = reviewsMapper.mapToReviews([reviewDto])

        then: "all fields are mapped correctly"
            reviews.size() == 1
            with(reviews[0]) {
                reviewerName == "Complete Reviewer"
                reviewerRating == 4
                reviewDate == LocalDate.of(2023, 12, 25)
                reviewText == "This is the review text with HTML."
                isVerifiedPurchase == true
                helpfulVotes == 5
                totalVotes == 10
                pros == ["Pro 1", "Pro 2"] as String[]
                cons == ["Con 1"] as String[]
                neutral == ["Neutral 1", "Neutral 2", "Neutral 3"] as String[]
            }
    }

    def "mapToReviews handles null and empty optional fields"() {
        given: "a review DTO with minimal fields"
            def reviewDto = new ReviewsApiResponseDto.ReviewDto(
                    id: 1L,
                    rating: 3,
                    authorName: "Minimal Reviewer",
                    reviewTime: "01/01/2024",
                    helpfulnessMessage: null,
                    originalFormattedReview: null,
                    aggregatedReviewData: null,
                    verified: null
            )

        when: "mapping to review"
            List<Review> reviews = reviewsMapper.mapToReviews([reviewDto])

        then: "optional fields are null or default"
            reviews.size() == 1
            with(reviews[0]) {
                reviewerName == "Minimal Reviewer"
                reviewerRating == 3
                reviewText == null
                isVerifiedPurchase == false
                helpfulVotes == 0
                totalVotes == 0
                pros == null
                cons == null
                neutral == null
            }
    }

    @Unroll
    def "mapToReviews handles verified purchase: #scenario"() {
        given: "a review DTO with verified = #verified"
            def reviewDto = new ReviewsApiResponseDto.ReviewDto(
                    id: 1L,
                    rating: 5,
                    authorName: "Test User",
                    reviewTime: "01/01/2024",
                    verified: verified
            )

        when: "mapping to review"
            List<Review> reviews = reviewsMapper.mapToReviews([reviewDto])

        then: "isVerifiedPurchase is set correctly"
            reviews[0].isVerifiedPurchase == expected

        where:
            scenario | verified || expected
            "true"   | true     || true
            "false"  | false    || false
            "null"   | null     || false
    }

    def "mapToReviews extracts text from HTML review"() {
        given: "a review DTO with HTML formatted review"
            def reviewDto = new ReviewsApiResponseDto.ReviewDto(
                    id: 1L,
                    rating: 5,
                    authorName: "HTML Reviewer",
                    reviewTime: "01/01/2024",
                    originalFormattedReview: "<div><p>First paragraph.</p><p>Second <em>paragraph</em> with <strong>formatting</strong>.</p></div>"
            )

        when: "mapping to review"
            List<Review> reviews = reviewsMapper.mapToReviews([reviewDto])

        then: "HTML is stripped and text is extracted"
            reviews[0].reviewText == "First paragraph. Second paragraph with formatting."
    }

    def "mapToReviews handles blank review text"() {
        given: "a review DTO with blank review text"
            def reviewDto = new ReviewsApiResponseDto.ReviewDto(
                    id: 1L,
                    rating: 5,
                    authorName: "Blank Reviewer",
                    reviewTime: "01/01/2024",
                    originalFormattedReview: "   "
            )

        when: "mapping to review"
            List<Review> reviews = reviewsMapper.mapToReviews([reviewDto])

        then: "review text is null"
            reviews[0].reviewText == null
    }

    def "mapToReviews handles empty HTML tags"() {
        given: "a review DTO with empty HTML"
            def reviewDto = new ReviewsApiResponseDto.ReviewDto(
                    id: 1L,
                    rating: 5,
                    authorName: "Empty HTML Reviewer",
                    reviewTime: "01/01/2024",
                    originalFormattedReview: "<p></p><div></div>"
            )

        when: "mapping to review"
            List<Review> reviews = reviewsMapper.mapToReviews([reviewDto])

        then: "review text is null"
            reviews[0].reviewText == null
    }

    @Unroll
    def "mapToReviews parses date '#dateInput' correctly"() {
        given: "a review DTO with date"
            def reviewDto = new ReviewsApiResponseDto.ReviewDto(
                    id: 1L,
                    rating: 5,
                    authorName: "Date Tester",
                    reviewTime: dateInput
            )

        when: "mapping to review"
            List<Review> reviews = reviewsMapper.mapToReviews([reviewDto])

        then: "date is parsed correctly"
            reviews[0].reviewDate == expectedDate

        where:
            dateInput      || expectedDate
            "01/01/2024"   || LocalDate.of(2024, 1, 1)
            "31/12/2023"   || LocalDate.of(2023, 12, 31)
            "15/06/2024"   || LocalDate.of(2024, 6, 15)
            "2024-01-01"   || LocalDate.of(2024, 1, 1)
            "2023-12-31"   || LocalDate.of(2023, 12, 31)
            "2024-06-15"   || LocalDate.of(2024, 6, 15)
    }

    @Unroll
    def "mapToReviews handles invalid date '#dateInput'"() {
        given: "a review DTO with invalid date"
            def reviewDto = new ReviewsApiResponseDto.ReviewDto(
                    id: 1L,
                    rating: 5,
                    authorName: "Invalid Date Tester",
                    reviewTime: dateInput
            )

        when: "mapping to review"
            List<Review> reviews = reviewsMapper.mapToReviews([reviewDto])

        then: "date is null"
            reviews[0].reviewDate == null

        and: "warning is logged"
            assertLog(Level.WARN, expectedLogMessage)

        where:
            dateInput          || expectedLogMessage
            "invalid"          || "Unexpected date format:"
            "01.01.2024"       || "Unexpected date format:"
            "2024/01/01"       || "Failed to parse review date"
            "32/01/2024"       || "Failed to parse review date"
            "01/13/2024"       || "Failed to parse review date"
            "abc/def/ghij"     || "Failed to parse review date"
    }

    @Unroll
    def "mapToReviews parses helpful votes '#helpfulText' correctly"() {
        given: "a review DTO with helpful votes"
            def reviewDto = new ReviewsApiResponseDto.ReviewDto(
                    id: 1L,
                    rating: 5,
                    authorName: "Votes Tester",
                    reviewTime: "01/01/2024",
                    helpfulnessMessage: helpfulText
            )

        when: "mapping to review"
            List<Review> reviews = reviewsMapper.mapToReviews([reviewDto])

        then: "votes are parsed correctly"
            reviews[0].helpfulVotes == expectedHelpful
            reviews[0].totalVotes == expectedTotal

        where:
            helpfulText                                      || expectedHelpful | expectedTotal
            "3 out of 5 found this review helpful"           || 3               | 5
            "1 out of 1 found this review helpful"           || 1               | 1
            "10 out of 20 found this review helpful"         || 10              | 20
            "0 out of 5 found this review helpful"           || 0               | 5
            "2 στους 4 χρήστες βρήκαν αυτή την κριτική"      || 2               | 4
            "5 στους 10 χρήστες βρήκαν αυτή την κριτική"     || 5               | 10
    }

    @Unroll
    def "mapToReviews handles invalid helpful votes '#helpfulText'"() {
        given: "a review DTO with invalid helpful votes"
            def reviewDto = new ReviewsApiResponseDto.ReviewDto(
                    id: 1L,
                    rating: 5,
                    authorName: "Invalid Votes Tester",
                    reviewTime: "01/01/2024",
                    helpfulnessMessage: helpfulText
            )

        when: "mapping to review"
            List<Review> reviews = reviewsMapper.mapToReviews([reviewDto])

        then: "votes default to 0"
            reviews[0].helpfulVotes == 0
            reviews[0].totalVotes == 0

        where:
            helpfulText              || _
            null                     || _
            "invalid format"         || _
            "abc out of xyz"         || _
            ""                       || _
    }

    def "mapToReviews handles empty aggregated review data lists"() {
        given: "a review DTO with empty lists"
            def reviewDto = new ReviewsApiResponseDto.ReviewDto(
                    id: 1L,
                    rating: 3,
                    authorName: "Empty Lists Tester",
                    reviewTime: "01/01/2024",
                    aggregatedReviewData: '<ul class="pros"></ul><ul class="cons"></ul><ul class="so-so"></ul>'
            )

        when: "mapping to review"
            List<Review> reviews = reviewsMapper.mapToReviews([reviewDto])

        then: "arrays are null when empty"
            reviews[0].pros == null
            reviews[0].cons == null
            reviews[0].neutral == null
    }
}
