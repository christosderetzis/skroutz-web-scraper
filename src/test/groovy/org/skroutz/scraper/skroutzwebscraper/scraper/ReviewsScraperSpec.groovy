package org.skroutz.scraper.skroutzwebscraper.scraper

import org.skroutz.scraper.skroutzwebscraper.entity.Review
import org.springframework.context.ApplicationContext
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class ReviewsScraperSpec extends Specification {

    ApplicationContext applicationContext = Mock()

    @Subject
    ReviewsScraper reviewsScraper = new ReviewsScraper(applicationContext)

    @Unroll
    def "parseDateText parses '#input' as #expected"() {
        given: "a review object"
            Review review = new Review()

        when: "parsing date text"
            reviewsScraper.parseDateText(input, review)

        then: "the review date is parsed correctly"
            review.reviewDate?.toString() == expected

        where:
            input              || expected
            "23/09/2023"       || "2023-09-23"
            "2025-09-23"       || "2025-09-23"
            "01/12/2024"       || "2024-12-01"
            "September 23, 25" || null
            "23/invalid/2023"  || null
            "invalid-date"     || null
    }

    @Unroll
    def "parseHelpfulVotes parses '#input' as helpfulVotes=#helpfulVotes, totalVotes=#totalVotes"() {
        given: "a review object"
            Review review = new Review()

        when: "parsing helpful votes text"
            reviewsScraper.parseHelpfulVotes(input, review)

        then: "the helpfulVotes and totalVotes are set as expected"
            review.helpfulVotes == helpfulVotes
            review.totalVotes == totalVotes

        where:
            input                                                        || helpfulVotes | totalVotes
            "3 out of 5 found this review helpful"                       || 3            | 5
            "1 out of 1 found this review helpful"                       || 1            | 1
            "10 out of 20 found this review helpful"                     || 10           | 20
            "2 στους 4 χρήστης βρήκε αυτή την κριτική χρήσιμη"           || 2            | 4
            "5 στους 10 χρήστης βρήκε αυτή την κριτική χρήσιμη"          || 5            | 10
            "helpfulness unknown"                                        || 0            | 0
            "abc out of xyz found this review helpful"                   || 0            | 0
            "invalid format"                                             || 0            | 0
    }

    @Unroll
    def "toArrayOrNull converts list correctly for #description"() {
        when: "converting list to array"
            String[] result = reviewsScraper.toArrayOrNull(input)

        then: "the result matches expected output"
            result == expected

        where:
            description      | input                              || expected
            "non-empty list" | ["Item 1", "Item 2"]               || ["Item 1", "Item 2"] as String[]
            "single item"    | ["Only one"]                       || ["Only one"] as String[]
            "empty list"     | []                                 || null
            "null input"     | null                               || null
            "mixed objects"  | ["String", 123, true]              || ["String", "123", "true"] as String[]
    }

    def "toArrayOrNull handles list with numbers"() {
        given: "a list with integer values"
            List<Integer> numbers = [1, 2, 3, 4, 5]

        when: "converting to array"
            String[] result = reviewsScraper.toArrayOrNull(numbers)

        then: "numbers are converted to strings"
            result == ["1", "2", "3", "4", "5"] as String[]
    }
}
