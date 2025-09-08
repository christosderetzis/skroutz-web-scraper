package org.skroutz.scraper.skroutzwebscraper.scraper

import org.openqa.selenium.*;
import org.skroutz.scraper.skroutzwebscraper.entity.Review
import org.springframework.context.ApplicationContext
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll


class ReviewsScraperSpec extends Specification {

    ApplicationContext applicationContext = Mock()

    @Subject
    ReviewsScraper reviewsScraper = new ReviewsScraper(applicationContext)

    def "extract review name - Happy Path"() {
        given: "a review object"
            Review review = new Review()

        and: "a review element with author name"
            WebElement reviewElement = Mock(WebElement)
            reviewElement.findElement(By.cssSelector("a.author")) >> Mock(WebElement) {
                getText() >> "John Doe"
            }

        when: "extracting review name"
            reviewsScraper.extractReviewerName(reviewElement, review)

        then: "the review name is extracted correctly"
            review.reviewerName == "John Doe"
    }

    def "extract cons - Happy Path"() {
        given: "a review object"
            Review review = new Review()

        and: "a review element with cons"
            WebElement reviewElement = Mock(WebElement)
            reviewElement.findElements(By.cssSelector("ul.icon.cons > li")) >> [
                Mock(WebElement) { getText() >> "Too expensive" },
                Mock(WebElement) { getText() >> "Battery life could be better" }
            ]

        when: "extracting cons"
            reviewsScraper.extractCons(reviewElement, review)

        then: "the cons are extracted correctly"
            review.cons.toList() == ["Too expensive", "Battery life could be better"]
    }

    def "extract cons - No cons present"() {
        given: "a review object"
            Review review = new Review()

        and: "a review element with no cons"
            WebElement reviewElement = Mock(WebElement)
            reviewElement.findElements(By.cssSelector("ul.icon.cons > li")) >> []

        when: "extracting cons"
            reviewsScraper.extractCons(reviewElement, review)

        then: "the cons list remains null"
            review.cons == null
    }

    def "extract pros - Happy Path"() {
        given: "a review object"
            Review review = new Review()

        and: "a review element with pros"
            WebElement reviewElement = Mock(WebElement)
            reviewElement.findElements(By.cssSelector("ul.icon.pros > li")) >> [
                    Mock(WebElement) { getText() >> "Value for money" },
                    Mock(WebElement) { getText() >> "Great battery life" }
            ]

        when: "extracting pros"
            reviewsScraper.extractPros(reviewElement, review)

        then: "the pros are extracted correctly"
            review.pros.toList() == ["Value for money", "Great battery life"]
    }

    def "extract pros - No pros present"() {
        given: "a review object"
            Review review = new Review()

        and: "a review element with no pros"
            WebElement reviewElement = Mock(WebElement)
            reviewElement.findElements(By.cssSelector("ul.icon.pros > li")) >> []

        when: "extracting pros"
            reviewsScraper.extractPros(reviewElement, review)

        then: "the pros list remains null"
            review.pros == null
    }

    def "extract neutral - Happy Path"() {
        given: "a review object"
        Review review = new Review()

        and: "a review element with neutral"
        WebElement reviewElement = Mock(WebElement)
        reviewElement.findElements(By.cssSelector("ul.icon.so-so > li")) >> [
                Mock(WebElement) { getText() >> "Just decent" },
                Mock(WebElement) { getText() >> "Battery Life could be better" }
        ]

        when: "extracting neutral"
        reviewsScraper.extractNeutral(reviewElement, review)

        then: "the neutral are extracted correctly"
        review.neutral.toList() == ["Just decent", "Battery Life could be better"]
    }

    def "extract neutral - No neutral present"() {
        given: "a review object"
            Review review = new Review()

        and: "a review element with neutral"
            WebElement reviewElement = Mock(WebElement)
            reviewElement.findElements(By.cssSelector("ul.icon.so-so > li")) >> []

        when: "extracting neutral"
            reviewsScraper.extractNeutral(reviewElement, review)

        then: "the neutral list remains null"
            review.neutral == null
    }

    @Unroll
    def "extractReviewDate parses '#input' as #expected"() {
        given: "a review object"
            Review review = new Review()
            WebElement reviewElement = Mock(WebElement)
            reviewElement.findElement(By.cssSelector(".permalink.js-review-permalink")) >> Mock(WebElement) {
                getText() >> input
            }

        when: "extracting review date"
            reviewsScraper.extractReviewDate(reviewElement, review)

        then: "the review date is parsed correctly"
            review.reviewDate?.toString() == expected

        where:
            input              || expected
            "23/09/2023"       || "2023-09-23"
            "2025-09-23"       || "2025-09-23"
            "September 23, 25" || null
            "23/invalid/2023"  || null
    }

    def "extractIsVerifiedPurchase sets true when verification mark is present"() {
        given: "a review object with verification mark"
            Review review = new Review()
            WebElement reviewElement = Mock(WebElement)
            reviewElement.findElement(By.cssSelector(".verification-mark")) >> Mock(WebElement)

        when: "extracting isVerifiedPurchase"
            reviewsScraper.extractIsVerifiedPurchase(reviewElement, review)

        then: "the isVerifiedPurchase is set to true"
            review.isVerifiedPurchase
    }

    def "extractIsVerifiedPurchase sets false when verification mark is absent"() {
        given: "a review object without verification mark"
            Review review = new Review()
            WebElement reviewElement = Mock(WebElement)
            reviewElement.findElement(By.cssSelector(".verification-mark")) >> { throw new NoSuchElementException("not found") }

        when: "extracting isVerifiedPurchase"
            reviewsScraper.extractIsVerifiedPurchase(reviewElement, review)

        then: "the isVerifiedPurchase is set to false"
            !review.isVerifiedPurchase
    }

    @Unroll
    def "extractHelpfulVotes parses '#input' as helpfulVotes=#helpfulVotes, totalVotes=#totalVotes"() {
        given: "a review element with helpfulness message"
            Review review = new Review()
            WebElement reviewElement = Mock(WebElement)
            WebElement helpfulVotesElement = Mock(WebElement)
            reviewElement.findElement(By.cssSelector(".helpfulness-message")) >> helpfulVotesElement
            helpfulVotesElement.getText() >> input

        when: "extracting helpful votes"
            reviewsScraper.extractHelpfulVotes(reviewElement, review)

        then: "the helpfulVotes and totalVotes are set as expected"
            review.helpfulVotes == helpfulVotes
            review.totalVotes == totalVotes

        where:
            input                                                        || helpfulVotes | totalVotes
            "3 out of 5 found this review helpful"                       || 3            | 5
            "2 στους 4 χρήστης βρήκε αυτή την κριτική χρήσιμη"           || 2            | 4
            "helpfulness unknown"                                        || 0            | 0
            "abc out of xyz found this review helpful"                   || 0            | 0
    }

    def "extractHelpfulVotes sets 0 when element not found"() {
        given: "a review element without helpfulness message"
            Review review = new Review()
            WebElement reviewElement = Mock(WebElement)
            reviewElement.findElement(By.cssSelector(".helpfulness-message")) >> { throw new NoSuchElementException("not found") }

        when: "extracting helpful votes"
            reviewsScraper.extractHelpfulVotes(reviewElement, review)

        then: "helpfulVotes and totalVotes are set to 0"
            review.helpfulVotes == 0
            review.totalVotes == 0
    }

    @Unroll
    def "extractReviewText sets review text for #desc"() {
        given: "a review element with paragraphs"
            Review review = new Review()
            WebElement reviewElement = Mock(WebElement)
            reviewElement.findElements(By.cssSelector(".review-body p")) >> paragraphs

        when: "extracting review text"
            reviewsScraper.extractReviewText(reviewElement, review)

        then: "the review text is set as expected"
            review.reviewText == expected

        where:
            desc             | paragraphs                                                                               | expected
            "multiple lines" | [Mock(WebElement) { getText() >> "Line 1" }, Mock(WebElement) { getText() >> "Line 2" }] | "Line 1\n\nLine 2"
            "single line"    | [Mock(WebElement) { getText() >> "Only one line" }]                                      | "Only one line"
            "no lines"       | []                                                                                       | null
    }

    @Unroll
    def "extractReviewRating sets reviewer rating for data-stars='#stars'"() {
        given: "a review element with data-stars attribute"
            Review review = new Review()
            WebElement reviewElement = Mock(WebElement)
            reviewElement.getDomAttribute("data-stars") >> stars

        when: "extracting review rating"
            reviewsScraper.extractReviewRating(reviewElement, review)

        then: "the reviewer rating is set as expected"
            review.reviewerRating == expected

        where:
            stars  || expected
            "5"    || 5
            "3"    || 3
            "0"    || 0
    }
}
