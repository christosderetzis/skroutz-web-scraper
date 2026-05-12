package org.skroutz.scraper.skroutzwebscraper.scraper

import ch.qos.logback.classic.Level
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.skroutz.scraper.skroutzwebscraper.base.WithLoggingBaseSpec
import org.skroutz.scraper.skroutzwebscraper.dto.ProductApiResponseDto
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.server.ResponseStatusException
import spock.lang.Subject

import java.util.concurrent.TimeUnit

class ProductsScraperSpec extends WithLoggingBaseSpec {

    MockWebServer mockWebServer
    WebClient webClient

    @Subject
    ProductsScraper productsScraper

    def setup() {
        mockWebServer = new MockWebServer()
        mockWebServer.start()

        webClient = WebClient.builder()
                .baseUrl(mockWebServer.url("/").toString())
                .build()

        productsScraper = new ProductsScraper(webClient, 500, 50, 3) // 500ms timeout for tests, 50ms retry delay, 3 max retries
    }

    def cleanup() {
        mockWebServer.shutdown()
    }

    def "fetchProductsPage successfully fetches product data"() {
        given: "a valid products response"
            String jsonResponse = '''
            {
                "skus": [
                    {
                        "id": 123,
                        "sku_url": "https://www.skroutz.gr/s/123/product",
                        "name": "Test Product",
                        "spec_summary": "Test description",
                        "price": "99.99",
                        "image_url": "https://example.com/image.jpg",
                        "review_score": "4.5",
                        "reviews_count": "100"
                    }
                ],
                "page": {
                    "total_pages": 5,
                    "current_page": 1
                }
            }
            '''

        and: "mock server enqueues successful response"
            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .setBody(jsonResponse))

        when: "fetching products page"
            String url = mockWebServer.url("/api/products").toString()
            ProductApiResponseDto result = productsScraper.fetchProductsPage(url)

        then: "result matches expected response"
            with(result) {
                it != null
                items.size() == 1
                items[0].skroutzId == 123L
                items[0].title == "Test Product"
                items[0].price == "99.99"
                page.totalPages == 5
                page.currentPage == 1
            }

        and: "logs indicate success"
            assertLog(Level.INFO, "Fetching products data from URL:")
            assertLog(Level.INFO, "Successfully fetched products")

        and: "mock server received the request"
            mockWebServer.requestCount == 1
    }

    def "fetchProductsPage handles 404 Not Found error"() {
        given: "mock server returns 404"
            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(404)
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .setBody('{"error": "Not Found"}'))

        when: "fetching products page"
            String url = mockWebServer.url("/api/products/999").toString()
            productsScraper.fetchProductsPage(url)

        then: "ResponseStatusException is thrown"
            def exception = thrown(ResponseStatusException)
            with(exception) {
                statusCode == HttpStatus.INTERNAL_SERVER_ERROR
                message.contains("Failed to fetch products")
            }

        and: "error is logged"
            assertLog(Level.INFO, "Fetching products data from URL:")
            assertLog(Level.ERROR, "Error fetching products. Status:")
            assertLog(Level.ERROR, "Error fetching products from URL:")

        and: "mock server received the request"
            mockWebServer.requestCount == 1
    }

    def "fetchProductsPage handles 400 Bad Request error"() {
        given: "mock server returns 400"
            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(400)
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .setBody('{"error": "Bad Request"}'))

        when: "fetching products page"
            String url = mockWebServer.url("/api/products/invalid").toString()
            productsScraper.fetchProductsPage(url)

        then: "ResponseStatusException is thrown"
            def exception = thrown(ResponseStatusException)
            with(exception) {
                statusCode == HttpStatus.INTERNAL_SERVER_ERROR
                message.contains("Failed to fetch products")
            }

        and: "error is logged"
            assertLog(Level.INFO, "Fetching products data from URL:")
            assertLog(Level.ERROR, "Error fetching products. Status:")
            assertLog(Level.ERROR, "Error fetching products from URL:")
    }

    def "fetchProductsPage handles 500 Internal Server Error"() {
        given: "mock server returns 500"
            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(500)
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .setBody('{"error": "Internal Server Error"}'))

        when: "fetching products page"
            String url = mockWebServer.url("/api/products").toString()
            productsScraper.fetchProductsPage(url)

        then: "ResponseStatusException is thrown"
            def exception = thrown(ResponseStatusException)
            with(exception) {
                statusCode == HttpStatus.INTERNAL_SERVER_ERROR
                message.contains("Failed to fetch products")
            }

        and: "error is logged"
            assertLog(Level.INFO, "Fetching products data from URL:")
            assertLog(Level.ERROR, "Error fetching products. Status:")
            assertLog(Level.ERROR, "Error fetching products from URL:")
    }

    def "fetchProductsPage handles 503 Service Unavailable error"() {
        given: "mock server returns 503"
            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(503)
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .setBody('{"error": "Service Unavailable"}'))

        when: "fetching products page"
            String url = mockWebServer.url("/api/products").toString()
            productsScraper.fetchProductsPage(url)

        then: "ResponseStatusException is thrown"
            def exception = thrown(ResponseStatusException)
            with(exception) {
                statusCode == HttpStatus.INTERNAL_SERVER_ERROR
                message.contains("Failed to fetch products")
            }

        and: "error is logged"
            assertLog(Level.INFO, "Fetching products data from URL:")
            assertLog(Level.ERROR, "Error fetching products. Status:")
            assertLog(Level.ERROR, "Error fetching products from URL:")
    }

    def "fetchProductsPage handles empty response body"() {
        given: "mock server returns empty body"
            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .setBody(""))

        when: "fetching products with empty response"
            String url = mockWebServer.url("/api/products").toString()
            productsScraper.fetchProductsPage(url)

        then: "ResponseStatusException is thrown"
            def exception = thrown(ResponseStatusException)
            with(exception) {
                statusCode == HttpStatus.INTERNAL_SERVER_ERROR
                message.contains("Failed to fetch products")
            }

        and: "error is logged"
            assertLog(Level.INFO, "Fetching products data from URL:")
            assertLog(Level.ERROR, "Error fetching products from URL:")
    }

    def "fetchProductsPage handles malformed JSON response"() {
        given: "mock server returns malformed JSON"
            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .setBody('{"invalid": json}'))

        when: "fetching products with malformed JSON"
            String url = mockWebServer.url("/api/products").toString()
            productsScraper.fetchProductsPage(url)

        then: "ResponseStatusException is thrown"
            def exception = thrown(ResponseStatusException)
            with(exception) {
                statusCode == HttpStatus.INTERNAL_SERVER_ERROR
                message.contains("Failed to fetch products")
            }

        and: "error is logged"
            assertLog(Level.INFO, "Fetching products data from URL:")
            assertLog(Level.ERROR, "Error fetching products from URL:")
    }

    def "fetchProductsPage handles connection timeout"() {
        given: "mock server disconnects after accepting connection"
            mockWebServer.enqueue(new MockResponse()
                    .setSocketPolicy(SocketPolicy.NO_RESPONSE))

        when: "fetching products with connection timeout"
            String url = mockWebServer.url("/api/products").toString()
            productsScraper.fetchProductsPage(url)

        then: "ResponseStatusException is thrown"
            def exception = thrown(ResponseStatusException)
            with(exception) {
                statusCode == HttpStatus.INTERNAL_SERVER_ERROR
                message.contains("Failed to fetch products")
            }

        and: "error is logged"
            assertLog(Level.INFO, "Fetching products data from URL:")
            assertLog(Level.ERROR, "Error fetching products from URL:")
    }

    def "fetchProductsPage handles network disconnect"() {
        given: "mock server disconnects during response"
            mockWebServer.enqueue(new MockResponse()
                    .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY))

        when: "fetching products with network disconnect"
            String url = mockWebServer.url("/api/products").toString()
            productsScraper.fetchProductsPage(url)

        then: "ResponseStatusException is thrown"
            def exception = thrown(ResponseStatusException)
            with(exception) {
                statusCode == HttpStatus.INTERNAL_SERVER_ERROR
                message.contains("Failed to fetch products")
            }

        and: "error is logged"
            assertLog(Level.INFO, "Fetching products data from URL:")
            assertLog(Level.ERROR, "Error fetching products from URL:")
    }

    def "fetchProductsPage handles slow response within timeout"() {
        given: "a valid response with delay"
            String jsonResponse = '''
            {
                "skus": [
                    {
                        "id": 123,
                        "name": "Test Product"
                    }
                ],
                "page": {
                    "total_pages": 1,
                    "current_page": 1
                }
            }
            '''

        and: "mock server returns response after delay (under timeout)"
            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .setBody(jsonResponse)
                    .setBodyDelay(200, TimeUnit.MILLISECONDS))

        when: "fetching products with slow response"
            String url = mockWebServer.url("/api/products").toString()
            ProductApiResponseDto result = productsScraper.fetchProductsPage(url)

        then: "request succeeds despite delay"
            with(result) {
                it != null
                items.size() == 1
                items[0].skroutzId == 123L
            }

        and: "logs indicate success"
            assertLog(Level.INFO, "Fetching products data from URL:")
            assertLog(Level.INFO, "Successfully fetched products")
    }

    def "fetchProductsPage handles null response"() {
        given: "mock server returns null-like response"
            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .setBody("null"))

        when: "fetching products with null response"
            String url = mockWebServer.url("/api/products").toString()
            productsScraper.fetchProductsPage(url)

        then: "ResponseStatusException is thrown"
            def exception = thrown(ResponseStatusException)
            with(exception) {
                statusCode == HttpStatus.INTERNAL_SERVER_ERROR
                message.contains("Null response for products")
            }

        and: "error is logged"
            assertLog(Level.INFO, "Fetching products data from URL:")
            assertLog(Level.ERROR, "Error fetching products from URL:")
    }

    def "fetchProductsPage handles complex nested product data"() {
        given: "a complex products response with multiple items"
            String jsonResponse = '''
            {
                "skus": [
                    {
                        "id": 123,
                        "sku_url": "https://www.skroutz.gr/s/123/product-one",
                        "name": "Product One",
                        "spec_summary": "Detailed description for product one",
                        "price": "199.99",
                        "image_url": "https://example.com/image1.jpg",
                        "review_score": "4.8",
                        "reviews_count": "250"
                    },
                    {
                        "id": 456,
                        "sku_url": "https://www.skroutz.gr/s/456/product-two",
                        "name": "Product Two",
                        "spec_summary": "Detailed description for product two",
                        "price": "299.99",
                        "image_url": "https://example.com/image2.jpg",
                        "review_score": "4.2",
                        "reviews_count": "89"
                    },
                    {
                        "id": 789,
                        "sku_url": "https://www.skroutz.gr/s/789/product-three",
                        "name": "Product Three",
                        "spec_summary": "Detailed description for product three",
                        "price": "149.50",
                        "image_url": "https://example.com/image3.jpg",
                        "review_score": "5.0",
                        "reviews_count": "500"
                    }
                ],
                "page": {
                    "total_pages": 10,
                    "current_page": 3
                }
            }
            '''

        and: "mock server enqueues complex response"
            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .setBody(jsonResponse))

        when: "fetching products"
            String url = mockWebServer.url("/api/products").toString()
            ProductApiResponseDto result = productsScraper.fetchProductsPage(url)

        then: "complex nested data is properly deserialized"
            with(result) {
                it != null
                items.size() == 3

                with(items[0]) {
                    skroutzId == 123L
                    title == "Product One"
                    description == "Detailed description for product one"
                    price == "199.99"
                    imageUrl == "https://example.com/image1.jpg"
                    rating == "4.8"
                    ratingCount == "250"
                }

                with(items[1]) {
                    skroutzId == 456L
                    title == "Product Two"
                    price == "299.99"
                    rating == "4.2"
                    ratingCount == "89"
                }

                with(items[2]) {
                    skroutzId == 789L
                    title == "Product Three"
                    price == "149.50"
                    rating == "5.0"
                    ratingCount == "500"
                }

                with(page) {
                    totalPages == 10
                    currentPage == 3
                }
            }

        and: "logs indicate success"
            assertLog(Level.INFO, "Successfully fetched products")
    }

    def "fetchProductsPage retries on 403 Forbidden and succeeds on first retry"() {
        given: "mock server returns 403 then 200"
            String jsonResponse = '''
            {
                "skus": [
                    {
                        "id": 123,
                        "name": "Test Product"
                    }
                ],
                "page": {}
            }
            '''

            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(403)
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .setBody('{"error": "Forbidden"}'))

            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .setBody(jsonResponse))

        when: "fetching products"
            String url = mockWebServer.url("/api/products").toString()
            ProductApiResponseDto result = productsScraper.fetchProductsPage(url)

        then: "request succeeds after retry"
            result != null
            result.items.size() == 1

        and: "retry log is present"
            assertLog(Level.ERROR, "Error fetching products. Status:")
            assertLog(Level.WARN, "Received 403 FORBIDDEN. Retrying attempt 1/3 after backoff...")

        and: "mock server received 2 requests"
            mockWebServer.requestCount == 2
    }

    def "fetchProductsPage retries on 403 Forbidden and succeeds on second retry"() {
        given: "mock server returns 403 twice then 200"
            String jsonResponse = '''
            {
                "skus": [
                    {
                        "id": 123,
                        "name": "Test Product"
                    }
                ],
                "page": {}
            }
            '''

            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(403)
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .setBody('{"error": "Forbidden"}'))

            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(403)
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .setBody('{"error": "Forbidden"}'))

            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .setBody(jsonResponse))

        when: "fetching products"
            String url = mockWebServer.url("/api/products").toString()
            ProductApiResponseDto result = productsScraper.fetchProductsPage(url)

        then: "request succeeds after retries"
            result != null
            result.items.size() == 1

        and: "retry logs are present"
            assertLog(Level.WARN, "Received 403 FORBIDDEN. Retrying attempt 1/3 after backoff...")
            assertLog(Level.WARN, "Received 403 FORBIDDEN. Retrying attempt 1/3 after backoff...")

        and: "mock server received 3 requests"
            mockWebServer.requestCount == 3
    }

    def "fetchProductsPage exhausts retries on 403 Forbidden and throws exception"() {
        given: "mock server returns 403 for all retries"
            4.times {
                mockWebServer.enqueue(new MockResponse()
                        .setResponseCode(403)
                        .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .setBody('{"error": "Forbidden"}'))
            }

        when: "fetching products"
            String url = mockWebServer.url("/api/products").toString()
            productsScraper.fetchProductsPage(url)

        then: "ResponseStatusException is thrown after all retries"
            def exception = thrown(ResponseStatusException)
            with(exception) {
                statusCode == HttpStatus.INTERNAL_SERVER_ERROR
                message.contains("Failed to fetch products")
            }

        and: "all retry logs are present"
            assertLog(Level.WARN, "Received 403 FORBIDDEN. Retrying attempt 1/3 after backoff...")
            assertLog(Level.WARN, "Received 403 FORBIDDEN. Retrying attempt 2/3 after backoff...")
            assertLog(Level.WARN, "Received 403 FORBIDDEN. Retrying attempt 3/3 after backoff...")
            assertLog(Level.ERROR, "Max retries (3) exhausted for URL:")

        and: "mock server received 4 requests (initial + 3 retries)"
            mockWebServer.requestCount == 4
    }

    def "fetchProductsPage does not retry on non-retryable 4xx errors"() {
        given: "mock server returns 404"
            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(404)
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .setBody('{"error": "Not Found"}'))

        when: "fetching products"
            String url = mockWebServer.url("/api/products").toString()
            productsScraper.fetchProductsPage(url)

        then: "ResponseStatusException is thrown immediately without retries"
            def exception = thrown(ResponseStatusException)
            exception.statusCode == HttpStatus.INTERNAL_SERVER_ERROR

        and: "mock server received only 1 request"
            mockWebServer.requestCount == 1
    }

    def "fetchProductsPage retries on 500 Internal Server Error"() {
        given: "mock server returns 500 twice then 200"
            String jsonResponse = '''
            {
                "skus": [
                    {
                        "id": 123,
                        "name": "Test Product"
                    }
                ],
                "page": {}
            }
            '''

            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(500)
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .setBody('{"error": "Internal Server Error"}'))

            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(500)
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .setBody('{"error": "Internal Server Error"}'))

            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .setBody(jsonResponse))

        when: "fetching products"
            String url = mockWebServer.url("/api/products").toString()
            ProductApiResponseDto result = productsScraper.fetchProductsPage(url)

        then: "request succeeds after retries"
            result != null
            result.items.size() == 1

        and: "retry logs are present"
            assertLog(Level.WARN, "Received 500 INTERNAL_SERVER_ERROR. Retrying attempt 1/3 after backoff...")
            assertLog(Level.WARN, "Received 500 INTERNAL_SERVER_ERROR. Retrying attempt 2/3 after backoff...")

        and: "mock server received 3 requests"
            mockWebServer.requestCount == 3
    }

    def "fetchProductsPage retries on 503 Service Unavailable"() {
        given: "mock server returns 503 then 200"
            String jsonResponse = '''
            {
                "skus": [
                    {
                        "id": 123,
                        "name": "Test Product"
                    }
                ],
                "page": {}
            }
            '''

            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(503)
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .setBody('{"error": "Service Unavailable"}'))

            mockWebServer.enqueue(new MockResponse()
                    .setResponseCode(200)
                    .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .setBody(jsonResponse))

        when: "fetching products"
            String url = mockWebServer.url("/api/products").toString()
            ProductApiResponseDto result = productsScraper.fetchProductsPage(url)

        then: "request succeeds after retry"
            result != null
            result.items.size() == 1

        and: "retry log is present"
            assertLog(Level.WARN, "Received 503 SERVICE_UNAVAILABLE. Retrying attempt 1/3 after backoff...")

        and: "mock server received 2 requests"
            mockWebServer.requestCount == 2
    }
}