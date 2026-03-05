package org.skroutz.scraper.skroutzwebscraper.scraper

import ch.qos.logback.classic.Level
import com.fasterxml.jackson.databind.ObjectMapper
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.skroutz.scraper.skroutzwebscraper.base.WithLoggingBaseSpec
import org.skroutz.scraper.skroutzwebscraper.dto.PriceHistoryResponseDto
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.server.ResponseStatusException
import spock.lang.Subject

import java.util.concurrent.TimeUnit

class PriceHistoryScraperSpec extends WithLoggingBaseSpec {

    MockWebServer mockWebServer
    WebClient webClient
    ObjectMapper objectMapper = new ObjectMapper()

    @Subject
    PriceHistoryScraper priceHistoryScraper

    def setup() {
        mockWebServer = new MockWebServer()
        mockWebServer.start()

        webClient = WebClient.builder()
                .baseUrl(mockWebServer.url("/").toString())
                .build()

        priceHistoryScraper = new PriceHistoryScraper(webClient, 1) // 1 second timeout for tests
    }

    def cleanup() {
        mockWebServer.shutdown()
    }

    def "fetchPriceHistory successfully fetches price history data"() {
        given: "a valid price history response"
            PriceHistoryResponseDto expectedResponse = PriceHistoryResponseDto.builder()
                    .minPrice(PriceHistoryResponseDto.MetricDataDto.builder()
                            .min(new BigDecimal("100.00"))
                            .max(new BigDecimal("200.00"))
                            .build())
                    .popularity(PriceHistoryResponseDto.MetricDataDto.builder()
                            .min(new BigDecimal("50"))
                            .max(new BigDecimal("150"))
                            .build())
                    .shopCount(PriceHistoryResponseDto.MetricDataDto.builder()
                            .min(new BigDecimal("5"))
                            .max(new BigDecimal("20"))
                            .build())
                    .build()

            String jsonResponse = objectMapper.writeValueAsString(expectedResponse)

        and: "mock server enqueues successful response"
            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .setBody(jsonResponse))

        when: "fetching price history"
            String url = mockWebServer.url("/api/price-history/123").toString()
            PriceHistoryResponseDto result = priceHistoryScraper.fetchPriceHistory(url)

        then: "result matches expected response"
            with(result) {
                it != null
                minPrice.min == expectedResponse.minPrice.min
                minPrice.max == expectedResponse.minPrice.max
                popularity.min == expectedResponse.popularity.min
                shopCount.max == expectedResponse.shopCount.max
            }

        and: "logs indicate success"
            assertLog(Level.INFO, "Fetching price history data from URL:")
            assertLog(Level.INFO, "Successfully fetched price history data")

        and: "mock server received the request"
            mockWebServer.requestCount == 1
    }

    def "fetchPriceHistory handles 404 Not Found error"() {
        given: "mock server returns 404"
            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(404)
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .setBody('{"error": "Not Found"}'))

        when: "fetching price history"
            String url = mockWebServer.url("/api/price-history/999").toString()
            priceHistoryScraper.fetchPriceHistory(url)

        then: "ResponseStatusException is thrown"
            def exception = thrown(ResponseStatusException)
            with(exception) {
                statusCode == HttpStatus.INTERNAL_SERVER_ERROR
                message.contains("Failed to fetch price history")
            }

        and: "error is logged"
            assertLog(Level.INFO, "Fetching price history data from URL:")
            assertLog(Level.ERROR, "Error fetching price history from URL:")

        and: "mock server received the request"
            mockWebServer.requestCount == 1
    }

    def "fetchPriceHistory handles 400 Bad Request error"() {
        given: "mock server returns 400"
            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(400)
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .setBody('{"error": "Bad Request"}'))

        when: "fetching price history"
            String url = mockWebServer.url("/api/price-history/invalid").toString()
            priceHistoryScraper.fetchPriceHistory(url)

        then: "ResponseStatusException is thrown"
            def exception = thrown(ResponseStatusException)
            with(exception) {
                statusCode == HttpStatus.INTERNAL_SERVER_ERROR
                message.contains("Failed to fetch price history")
            }

        and: "error is logged"
            assertLog(Level.INFO, "Fetching price history data from URL:")
            assertLog(Level.ERROR, "Error fetching price history from URL:")
    }

    def "fetchPriceHistory handles 500 Internal Server Error"() {
        given: "mock server returns 500"
            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(500)
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .setBody('{"error": "Internal Server Error"}'))

        when: "fetching price history"
            String url = mockWebServer.url("/api/price-history/123").toString()
            priceHistoryScraper.fetchPriceHistory(url)

        then: "ResponseStatusException is thrown"
            def exception = thrown(ResponseStatusException)
            with(exception) {
                statusCode == HttpStatus.INTERNAL_SERVER_ERROR
                message.contains("Failed to fetch price history")
            }

        and: "error is logged"
            assertLog(Level.INFO, "Fetching price history data from URL:")
            assertLog(Level.ERROR, "Error fetching price history from URL:")
    }

    def "fetchPriceHistory handles 503 Service Unavailable error"() {
        given: "mock server returns 503"
            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(503)
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .setBody('{"error": "Service Unavailable"}'))

        when: "fetching price history"
            String url = mockWebServer.url("/api/price-history/123").toString()
            priceHistoryScraper.fetchPriceHistory(url)

        then: "ResponseStatusException is thrown"
            def exception = thrown(ResponseStatusException)
            with(exception) {
                statusCode == HttpStatus.INTERNAL_SERVER_ERROR
                message.contains("Failed to fetch price history")
            }

        and: "error is logged"
            assertLog(Level.INFO, "Fetching price history data from URL:")
            assertLog(Level.ERROR, "Error fetching price history from URL:")
    }

    def "fetchPriceHistory handles empty response body"() {
        given: "mock server returns empty body"
            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .setBody(""))

        when: "fetching price history with empty response"
            String url = mockWebServer.url("/api/price-history/123").toString()
            priceHistoryScraper.fetchPriceHistory(url)

        then: "ResponseStatusException is thrown"
            def exception = thrown(ResponseStatusException)
            with(exception) {
                statusCode == HttpStatus.INTERNAL_SERVER_ERROR
                message.contains("Failed to fetch price history")
            }

        and: "error is logged"
            assertLog(Level.INFO, "Fetching price history data from URL:")
            assertLog(Level.ERROR, "Error fetching price history from URL:")
    }

    def "fetchPriceHistory handles malformed JSON response"() {
        given: "mock server returns malformed JSON"
            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .setBody('{"invalid": json}'))

        when: "fetching price history with malformed JSON"
            String url = mockWebServer.url("/api/price-history/123").toString()
            priceHistoryScraper.fetchPriceHistory(url)

        then: "ResponseStatusException is thrown"
            def exception = thrown(ResponseStatusException)
            with(exception) {
                statusCode == HttpStatus.INTERNAL_SERVER_ERROR
                message.contains("Failed to fetch price history")
            }

        and: "error is logged"
            assertLog(Level.INFO, "Fetching price history data from URL:")
            assertLog(Level.ERROR, "Error fetching price history from URL:")
    }

    def "fetchPriceHistory handles connection timeout"() {
        given: "mock server disconnects after accepting connection"
            mockWebServer.enqueue(new MockResponse()
                    .setSocketPolicy(SocketPolicy.NO_RESPONSE))

        when: "fetching price history with connection timeout"
            String url = mockWebServer.url("/api/price-history/123").toString()
            priceHistoryScraper.fetchPriceHistory(url)

        then: "ResponseStatusException is thrown"
            def exception = thrown(ResponseStatusException)
            with(exception) {
                statusCode == HttpStatus.INTERNAL_SERVER_ERROR
                message.contains("Failed to fetch price history")
            }

        and: "error is logged"
            assertLog(Level.INFO, "Fetching price history data from URL:")
            assertLog(Level.ERROR, "Error fetching price history from URL:")
    }

    def "fetchPriceHistory handles network disconnect"() {
        given: "mock server disconnects during response"
            mockWebServer.enqueue(new MockResponse()
                    .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY))

        when: "fetching price history with network disconnect"
            String url = mockWebServer.url("/api/price-history/123").toString()
            priceHistoryScraper.fetchPriceHistory(url)

        then: "ResponseStatusException is thrown"
            def exception = thrown(ResponseStatusException)
            with(exception) {
                statusCode == HttpStatus.INTERNAL_SERVER_ERROR
                message.contains("Failed to fetch price history")
            }

        and: "error is logged"
            assertLog(Level.INFO, "Fetching price history data from URL:")
            assertLog(Level.ERROR, "Error fetching price history from URL:")
    }

    def "fetchPriceHistory handles slow response within timeout"() {
        given: "a valid response with delay"
            PriceHistoryResponseDto expectedResponse = PriceHistoryResponseDto.builder()
                    .minPrice(PriceHistoryResponseDto.MetricDataDto.builder().build())
                    .build()

            String jsonResponse = objectMapper.writeValueAsString(expectedResponse)

        and: "mock server returns response after delay (under timeout)"
            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .setBody(jsonResponse)
                    .setBodyDelay(200, TimeUnit.MILLISECONDS))

        when: "fetching price history with slow response"
            String url = mockWebServer.url("/api/price-history/123").toString()
            PriceHistoryResponseDto result = priceHistoryScraper.fetchPriceHistory(url)

        then: "request succeeds despite delay"
            with(result) {
                it != null
                minPrice != null
            }

        and: "logs indicate success"
            assertLog(Level.INFO, "Fetching price history data from URL:")
            assertLog(Level.INFO, "Successfully fetched price history data")
    }

    def "fetchPriceHistory handles complex nested price history data"() {
        given: "a complex price history response with all nested data"
            PriceHistoryResponseDto expectedResponse = PriceHistoryResponseDto.builder()
                    .minPrice(PriceHistoryResponseDto.MetricDataDto.builder()
                            .min(new BigDecimal("99.99"))
                            .max(new BigDecimal("299.99"))
                            .graphData(PriceHistoryResponseDto.GraphDataDto.builder()
                                    .oneMonth(PriceHistoryResponseDto.TimePeriodDto.builder()
                                            .hasValues(true)
                                            .label("1 month")
                                            .values(List.of(
                                                    PriceHistoryResponseDto.DataPointDto.builder()
                                                            .timestamp(1234567890L)
                                                            .value(new BigDecimal("150.00"))
                                                            .shopName("Shop A")
                                                            .build()
                                            ))
                                            .build())
                                    .build())
                            .build())
                    .popularity(PriceHistoryResponseDto.MetricDataDto.builder()
                            .min(new BigDecimal("10"))
                            .max(new BigDecimal("100"))
                            .build())
                    .shopCount(PriceHistoryResponseDto.MetricDataDto.builder()
                            .min(new BigDecimal("3"))
                            .max(new BigDecimal("15"))
                            .build())
                    .build()

            String jsonResponse = objectMapper.writeValueAsString(expectedResponse)

        and: "mock server enqueues complex response"
            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .setBody(jsonResponse))

        when: "fetching price history"
            String url = mockWebServer.url("/api/price-history/123").toString()
            PriceHistoryResponseDto result = priceHistoryScraper.fetchPriceHistory(url)

        then: "complex nested data is properly deserialized"
            with(result) {
                it != null
                minPrice.graphData != null

                with(minPrice.graphData.oneMonth) {
                    it != null
                    hasValues == true
                    label == "1 month"
                    values.size() == 1

                    with(values[0]) {
                        shopName == "Shop A"
                        value == new BigDecimal("150.00")
                    }
                }
            }

        and: "logs indicate success"
            assertLog(Level.INFO, "Successfully fetched price history data")
    }
}
