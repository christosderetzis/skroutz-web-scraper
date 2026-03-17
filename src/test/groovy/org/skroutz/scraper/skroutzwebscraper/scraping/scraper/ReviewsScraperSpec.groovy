package org.skroutz.scraper.skroutzwebscraper.scraping.scraper

import ch.qos.logback.classic.Level
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.skroutz.scraper.skroutzwebscraper.base.WithLoggingBaseSpec
import org.skroutz.scraper.skroutzwebscraper.scraping.dto.ReviewsApiResponseDto
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.server.ResponseStatusException
import spock.lang.Subject

import java.util.concurrent.TimeUnit

class ReviewsScraperSpec extends WithLoggingBaseSpec {

    MockWebServer mockWebServer
    WebClient webClient

    @Subject
    ReviewsScraper reviewsScraper

    def setup() {
        mockWebServer = new MockWebServer()
        mockWebServer.start()
        webClient = WebClient.builder()
                .baseUrl(mockWebServer.url("/").toString())
                .build()
        reviewsScraper = new ReviewsScraper(webClient, 1)
    }

    def cleanup() {
        mockWebServer.shutdown()
    }

    def "Happy path, scrape reviews from single page"() {
        given: "a product URL and mock response with reviews"
            String productUrl = mockWebServer.url("/product").toString()
            String jsonResponse = '''
            {
                "reviews": {
                    "reviews": [
                        {
                            "id": 1,
                            "rating": 5,
                            "author_name": "John Doe",
                            "review_time": "01/01/2024",
                            "original_formatted_review": "<p>Great product!</p>"
                        },
                        {
                            "id": 2,
                            "rating": 4,
                            "author_name": "Jane Smith",
                            "review_time": "02/01/2024",
                            "original_formatted_review": "<p>Good value</p>"
                        }
                    ]
                }
            }
            '''

        and: "second page returns empty reviews"
            String emptyResponse = '''
            {
                "reviews": {
                    "reviews": []
                }
            }
            '''

            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .setBody(jsonResponse))
            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .setBody(emptyResponse))

        when: "scrapeReviews is called"
            List<ReviewsApiResponseDto.ReviewDto> reviews = reviewsScraper.scrapeReviews(productUrl)

        then: "reviews are fetched and returned"
            reviews.size() == 2
            reviews[0].authorName == "John Doe"
            reviews[0].rating == 5
            reviews[1].authorName == "Jane Smith"
            reviews[1].rating == 4

        and: "logs indicate success"
            assertLog(Level.INFO, "Scraping reviews for")
            assertLog(Level.INFO, "Total reviews fetched: 2")
    }

    def "Happy path, scrape reviews from multiple pages"() {
        given: "a product URL"
            String productUrl = mockWebServer.url("/product").toString()

        and: "first page with reviews"
            String firstPageResponse = '''
            {
                "reviews": {
                    "reviews": [
                        {"id": 1, "rating": 5, "author_name": "User1"},
                        {"id": 2, "rating": 4, "author_name": "User2"}
                    ]
                }
            }
            '''

        and: "second page with more reviews"
            String secondPageResponse = '''
            {
                "reviews": {
                    "reviews": [
                        {"id": 3, "rating": 3, "author_name": "User3"}
                    ]
                }
            }
            '''

        and: "third page empty"
            String emptyResponse = '''
            {
                "reviews": {
                    "reviews": []
                }
            }
            '''

            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .setBody(firstPageResponse))
            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .setBody(secondPageResponse))
            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .setBody(emptyResponse))

        when: "scrapeReviews is called"
            List<ReviewsApiResponseDto.ReviewDto> reviews = reviewsScraper.scrapeReviews(productUrl)

        then: "all reviews from all pages are returned"
            reviews.size() == 3
            reviews*.authorName == ["User1", "User2", "User3"]

        and: "logs indicate total count"
            assertLog(Level.INFO, "Total reviews fetched: 3")
    }

    def "Happy path, product URL with .html extension"() {
        given: "a product URL with .html extension"
            String productUrl = mockWebServer.url("/product.html").toString()
            String emptyResponse = '''
            {
                "reviews": {
                    "reviews": []
                }
            }
            '''
            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .setBody(emptyResponse))

        when: "scrapeReviews is called"
            List<ReviewsApiResponseDto.ReviewDto> reviews = reviewsScraper.scrapeReviews(productUrl)

        then: "URL is correctly transformed to reviews.json"
            reviews.isEmpty()
            def request = mockWebServer.takeRequest()
            request.path.contains("/product/reviews.json")
    }

    def "Happy path, no reviews available"() {
        given: "a product URL with no reviews"
            String productUrl = mockWebServer.url("/product").toString()
            String emptyResponse = '''
            {
                "reviews": {
                    "reviews": []
                }
            }
            '''
            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .setBody(emptyResponse))

        when: "scrapeReviews is called"
            List<ReviewsApiResponseDto.ReviewDto> reviews = reviewsScraper.scrapeReviews(productUrl)

        then: "empty list is returned"
            reviews.isEmpty()

        and: "logs indicate zero reviews"
            assertLog(Level.INFO, "Total reviews fetched: 0")
    }

    def "fetchReviewPage returns response successfully"() {
        given: "a valid API URL"
            String url = mockWebServer.url("/reviews.json").toString()
            String jsonResponse = '''
            {
                "reviews": {
                    "reviews": [
                        {"id": 1, "rating": 5, "author_name": "Test User"}
                    ]
                }
            }
            '''
            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .setBody(jsonResponse))

        when: "fetchReviewPage is called"
            ReviewsApiResponseDto response = reviewsScraper.fetchReviewPage(url)

        then: "response is returned"
            response != null
            response.reviews.reviews.size() == 1
            response.reviews.reviews[0].authorName == "Test User"
    }

    def "fetchReviewPage handles #statusCode #statusName error"() {
        given: "a URL that returns $statusCode"
            String url = mockWebServer.url("/reviews.json").toString()
            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(statusCode)
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .setBody("{\"error\": \"$statusName\"}"))

        when: "fetchReviewPage is called"
            reviewsScraper.fetchReviewPage(url)

        then: "ResponseStatusException is thrown"
            ResponseStatusException ex = thrown(ResponseStatusException)
            ex.statusCode == HttpStatus.INTERNAL_SERVER_ERROR

        and: "error is logged"
            assertLog(Level.ERROR, "Error fetching review data from URL:")

        where:
            statusCode | statusName
            400        | "Bad Request"
            404        | "Not Found"
            500        | "Internal Server Error"
            503        | "Service Unavailable"
    }

    def "fetchReviewPage handles empty response body"() {
        given: "a URL that returns empty body"
            String url = mockWebServer.url("/reviews.json").toString()
            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .setBody(""))

        when: "fetchReviewPage is called"
            reviewsScraper.fetchReviewPage(url)

        then: "ResponseStatusException is thrown"
            ResponseStatusException ex = thrown(ResponseStatusException)
            ex.statusCode == HttpStatus.INTERNAL_SERVER_ERROR

        and: "error is logged"
            assertLog(Level.ERROR, "Error fetching review data from URL:")
    }

    def "fetchReviewPage handles malformed JSON response"() {
        given: "a URL that returns malformed JSON"
            String url = mockWebServer.url("/reviews.json").toString()
            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .setBody('{"invalid": json}'))

        when: "fetchReviewPage is called"
            reviewsScraper.fetchReviewPage(url)

        then: "ResponseStatusException is thrown"
            ResponseStatusException ex = thrown(ResponseStatusException)
            ex.statusCode == HttpStatus.INTERNAL_SERVER_ERROR

        and: "error is logged"
            assertLog(Level.ERROR, "Error fetching review data from URL:")
    }

    def "fetchReviewPage handles connection timeout"() {
        given: "a URL where server doesn't respond"
            String url = mockWebServer.url("/reviews.json").toString()
            mockWebServer.enqueue(new MockResponse()
                    .setSocketPolicy(SocketPolicy.NO_RESPONSE))

        when: "fetchReviewPage is called"
            reviewsScraper.fetchReviewPage(url)

        then: "ResponseStatusException is thrown"
            ResponseStatusException ex = thrown(ResponseStatusException)
            ex.statusCode == HttpStatus.INTERNAL_SERVER_ERROR

        and: "error is logged"
            assertLog(Level.ERROR, "Error fetching review data from URL:")
    }

    def "fetchReviewPage handles network disconnect during response"() {
        given: "a URL where server disconnects during response"
            String url = mockWebServer.url("/reviews.json").toString()
            mockWebServer.enqueue(new MockResponse()
                    .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY))

        when: "fetchReviewPage is called"
            reviewsScraper.fetchReviewPage(url)

        then: "ResponseStatusException is thrown"
            ResponseStatusException ex = thrown(ResponseStatusException)
            ex.statusCode == HttpStatus.INTERNAL_SERVER_ERROR

        and: "error is logged"
            assertLog(Level.ERROR, "Error fetching review data from URL:")
    }

    def "fetchReviewPage handles slow response within timeout"() {
        given: "a valid response with delay"
            String url = mockWebServer.url("/reviews.json").toString()
            String jsonResponse = '''
            {
                "reviews": {
                    "reviews": [
                        {"id": 1, "rating": 5, "author_name": "Test User"}
                    ]
                }
            }
            '''
            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .setBody(jsonResponse)
                    .setBodyDelay(200, TimeUnit.MILLISECONDS))

        when: "fetchReviewPage is called"
            ReviewsApiResponseDto response = reviewsScraper.fetchReviewPage(url)

        then: "request succeeds despite delay"
            response != null
            response.reviews.reviews.size() == 1
    }

    def "scrapeReviews accumulates offset correctly"() {
        given: "a product URL"
            String productUrl = mockWebServer.url("/product").toString()

        and: "responses that return varying page sizes"
            String firstPageResponse = '''
            {
                "reviews": {
                    "reviews": [
                        {"id": 1, "rating": 5, "author_name": "User1"},
                        {"id": 2, "rating": 4, "author_name": "User2"},
                        {"id": 3, "rating": 3, "author_name": "User3"}
                    ]
                }
            }
            '''
            String secondPageResponse = '''
            {
                "reviews": {
                    "reviews": [
                        {"id": 4, "rating": 2, "author_name": "User4"},
                        {"id": 5, "rating": 1, "author_name": "User5"}
                    ]
                }
            }
            '''
            String emptyResponse = '''
            {
                "reviews": {
                    "reviews": []
                }
            }
            '''

            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .setBody(firstPageResponse))
            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .setBody(secondPageResponse))
            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .setBody(emptyResponse))

        when: "scrapeReviews is called"
            List<ReviewsApiResponseDto.ReviewDto> reviews = reviewsScraper.scrapeReviews(productUrl)

        then: "all reviews are accumulated"
            reviews.size() == 5

        and: "correct offsets are used in requests"
            def request1 = mockWebServer.takeRequest()
            def request2 = mockWebServer.takeRequest()
            def request3 = mockWebServer.takeRequest()
            request1.path.contains("offset=0")
            request2.path.contains("offset=3")
            request3.path.contains("offset=5")
    }

    def "fetchReviewPage handles complex nested review data"() {
        given: "a complex review response with all fields"
            String url = mockWebServer.url("/reviews.json").toString()
            String jsonResponse = '''
            {
                "reviews": {
                    "reviews": [
                        {
                            "id": 123,
                            "rating": 5,
                            "author_name": "Detailed Reviewer",
                            "review_time": "15/06/2024",
                            "helpfulness_message": "3 out of 5 found this review helpful",
                            "original_formatted_review": "<p>This is a <strong>detailed</strong> review with HTML formatting.</p>",
                            "helpful_votes_count": 3,
                            "aggregated_review_data": "<ul class=\\"pros\\"><li>Pro 1</li><li>Pro 2</li></ul><ul class=\\"cons\\"><li>Con 1</li></ul>",
                            "purchased": true
                        }
                    ],
                    "merged_review_notices": {
                        "notice1": "Some notice"
                    }
                }
            }
            '''
            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .setBody(jsonResponse))

        when: "fetchReviewPage is called"
            ReviewsApiResponseDto response = reviewsScraper.fetchReviewPage(url)

        then: "complex nested data is properly deserialized"
            with(response) {
                it != null
                reviews != null
                reviews.reviews.size() == 1

                with(reviews.reviews[0]) {
                    id == 123
                    rating == 5
                    authorName == "Detailed Reviewer"
                    reviewTime == "15/06/2024"
                    helpfulnessMessage == "3 out of 5 found this review helpful"
                    originalFormattedReview.contains("detailed")
                    helpfulVotesCount == 3
                    aggregatedReviewData.contains("pros")
                    verified == true
                }

                reviews.mergedReviewNotices != null
                reviews.mergedReviewNotices["notice1"] == "Some notice"
            }
    }
}
