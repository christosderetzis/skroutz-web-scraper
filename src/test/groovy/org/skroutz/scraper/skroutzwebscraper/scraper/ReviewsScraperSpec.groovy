package org.skroutz.scraper.skroutzwebscraper.scraper

import ch.qos.logback.classic.Level
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.skroutz.scraper.skroutzwebscraper.base.WithLoggingBaseSpec
import org.skroutz.scraper.skroutzwebscraper.dto.ReviewsApiResponseDto
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
        reviewsScraper = new ReviewsScraper(webClient, 500, 50, 3) // 500ms timeout for tests, 50ms retry delay, 3 max retries
    }

    def cleanup() {
        mockWebServer.shutdown()
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
            assertLog(Level.ERROR, "Error fetching reviews from URL:")

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
            assertLog(Level.ERROR, "Error fetching reviews from URL:")
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
            assertLog(Level.ERROR, "Error fetching reviews from URL:")
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
            assertLog(Level.ERROR, "Error fetching reviews from URL:")
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
            assertLog(Level.ERROR, "Error fetching reviews from URL:")
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
                    .setBodyDelay(20, TimeUnit.MILLISECONDS))

        when: "fetchReviewPage is called"
            ReviewsApiResponseDto response = reviewsScraper.fetchReviewPage(url)

        then: "request succeeds despite delay"
            response != null
            response.reviews.reviews.size() == 1
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

    def "fetchReviewPage retries on 403 Forbidden and succeeds on first retry"() {
        given: "a URL that returns 403 Forbidden on first attempt and succeeds on retry"
            String url = mockWebServer.url("/reviews.json").toString()
            String jsonResponse = '''
            {
                "reviews": {
                    "reviews": [
                        {"id": 1, "rating": 5, "author_name": "Retry User"}
                    ]
                }
            }
            '''
            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(403)
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .setBody("{\"error\": \"Forbidden\"}"))
            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .setBody(jsonResponse))

        when: "fetchReviewPage is called"
            ReviewsApiResponseDto response = reviewsScraper.fetchReviewPage(url)

        then: "request is retried and eventually succeeds"
            response != null
            response.reviews.reviews.size() == 1
            response.reviews.reviews[0].authorName == "Retry User"

        and: "logs indicate retry attempt"
            assertLog(Level.ERROR, "Error fetching reviews. Status: 403 FORBIDDEN")
            assertLog(Level.WARN, "Received 403 FORBIDDEN. Retrying attempt 1/3 after backoff...")

        and: "mock server received two requests"
            mockWebServer.requestCount == 2
    }

    def "fetchReviewPage retries on 403 Forbidden and succeeds on second retry"() {
        given: "a URL that returns 403 Forbidden on first two attempts and succeeds on third attempt"
            String url = mockWebServer.url("/reviews.json").toString()
            String jsonResponse = '''
            {
                "reviews": {
                    "reviews": [
                        {"id": 1, "rating": 5, "author_name": "Retry User"}
                    ]
                }
            }
            '''
            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(403)
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .setBody("{\"error\": \"Forbidden\"}"))
            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(403)
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .setBody("{\"error\": \"Forbidden\"}"))
            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .setBody(jsonResponse))

        when: "fetchReviewPage is called"
            ReviewsApiResponseDto response = reviewsScraper.fetchReviewPage(url)

        then: "request is retried twice and eventually succeeds"
            response != null
            response.reviews.reviews.size() == 1
            response.reviews.reviews[0].authorName == "Retry User"

        and: "logs indicate retry attempts"
            assertLog(Level.ERROR, "Error fetching reviews. Status: 403 FORBIDDEN")
            assertLog(Level.WARN, "Received 403 FORBIDDEN. Retrying attempt 1/3 after backoff...")
            assertLog(Level.ERROR, "Error fetching reviews. Status: 403 FORBIDDEN")
            assertLog(Level.WARN, "Received 403 FORBIDDEN. Retrying attempt 2/3 after backoff...")

        and: "mock server received three requests"
            mockWebServer.requestCount == 3
    }

    def "fetchReviewPage retries on 403 Forbidden and fails after max retries"() {
        given: "a URL that returns 403 Forbidden on all retry attempts"
            String url = mockWebServer.url("/reviews.json").toString()
            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(403)
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .setBody("{\"error\": \"Forbidden\"}"))
            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(403)
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .setBody("{\"error\": \"Forbidden\"}"))
            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(403)
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .setBody("{\"error\": \"Forbidden\"}"))

        when: "fetchReviewPage is called"
            reviewsScraper.fetchReviewPage(url)

        then: "request is retried the maximum number of times and eventually fails"
            ResponseStatusException ex = thrown(ResponseStatusException)
            with(ex) {
                statusCode == HttpStatus.INTERNAL_SERVER_ERROR
                message.contains("Failed to fetch reviews")
            }

        and: "logs indicate all retry attempts"
            assertLog(Level.ERROR, "Error fetching reviews. Status: 403 FORBIDDEN")
            assertLog(Level.WARN, "Received 403 FORBIDDEN. Retrying attempt 1/3 after backoff...")
            assertLog(Level.ERROR, "Error fetching reviews. Status: 403 FORBIDDEN")
            assertLog(Level.WARN, "Received 403 FORBIDDEN. Retrying attempt 2/3 after backoff...")
            assertLog(Level.ERROR, "Error fetching reviews. Status: 403 FORBIDDEN")
            assertLog(Level.WARN, "Received 403 FORBIDDEN. Retrying attempt 3/3 after backoff...")

        and: "mock server received four requests (1 initial + 3 retries)"
            mockWebServer.requestCount == 4
    }
}
