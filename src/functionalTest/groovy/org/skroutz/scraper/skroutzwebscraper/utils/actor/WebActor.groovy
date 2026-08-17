package org.skroutz.scraper.skroutzwebscraper.utils.actor

import org.skroutz.scraper.skroutzwebscraper.category.infrastructure.dto.CategorySchemaCreateRequestDto
import org.skroutz.scraper.skroutzwebscraper.search.infrastructure.dto.ProductSearchRequest
import org.skroutz.scraper.skroutzwebscraper.scraping.infrastructure.dto.ScraperRequestDto
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.reactive.function.client.WebClient
import reactor.util.retry.Retry
import spock.util.concurrent.PollingConditions

import java.time.Duration

class WebActor {

    WebTestClient webTestClient
    private final int port

    WebActor(int port) {
        this.port = port
        this.webTestClient = WebTestClient.bindToServer()
                .baseUrl("http://localhost:${port}")
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .responseTimeout(Duration.ofSeconds(100))
                .build()
    }

    private WebTestClient clientFor(String accessToken) {
        return WebTestClient.bindToServer()
                .baseUrl("http://localhost:${port}")
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer ${accessToken}")
                .responseTimeout(Duration.ofSeconds(100))
                .build()
    }

    // Keycloak runs on 8083 via infra/docker-compose-functional-tests.yml. The realm is
    // auto-imported on startup from src/functionalTest/resources/keycloak/realm-export.json.
    // Here we just exchange admin/admin (role SUPER_ADMIN) for a bearer token.
    static String getAccessToken(String username = "admin", String password = "admin") {
        def client = WebClient.builder().baseUrl("http://localhost:8083").build()

        def responseEntity = client.post()
                .uri("/realms/skroutz-scraper/protocol/openid-connect/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .bodyValue("grant_type=password&client_id=skroutz-scraper-client&username=${username}&password=${password}")
                .retrieve()
                .toEntity(Map)
                .retryWhen(Retry.fixedDelay(30, Duration.ofSeconds(2)))
                .block()

        def response = responseEntity?.body as Map
        return response?.access_token as String
    }

    WebTestClient.ResponseSpec scrapeProducts(ScraperRequestDto requestDto, Boolean multiple = false, String accessToken = getAccessToken()) {
        return clientFor(accessToken).post()
                .uri(uriBuilder -> {
                    uriBuilder.path("/scraper/products")
                            .queryParamIfPresent("multiple", Optional.ofNullable(multiple))
                            .build()
                })
                .bodyValue(requestDto)
                .exchange()
    }

    WebTestClient.ResponseSpec getProductById(Long id) {
        return webTestClient.get()
                .uri("/products/{id}", id)
                .exchange()
    }

    WebTestClient.ResponseSpec getProductReviews(Long id, String sort = "helpful" , Integer page = null, Integer size = null) {
        return webTestClient.get()
                .uri(uriBuilder -> {
                    uriBuilder.pathSegment("products", id.toString(), "reviews")
                            .queryParam("sort", sort)
                            .queryParamIfPresent("page", Optional.ofNullable(page))
                            .queryParamIfPresent("size", Optional.ofNullable(size))
                            .build()
                })
                .exchange()
    }

    WebTestClient.ResponseSpec scrapePriceHistory(String accessToken = getAccessToken()) {
        return clientFor(accessToken).post()
                .uri("/scraper/price-history")
                .exchange()
    }

    WebTestClient.ResponseSpec scrapeSpecifications(String accessToken = getAccessToken()) {
        return clientFor(accessToken).post()
                .uri("/scraper/specifications")
                .exchange()
    }

    WebTestClient.ResponseSpec scrapeReviews(String accessToken = getAccessToken()) {
        return clientFor(accessToken).post()
                .uri("/scraper/reviews")
                .exchange()
    }

    WebTestClient.ResponseSpec summarizeReviews(Long productId) {
        return webTestClient.post()
                .uri("/products/{id}/reviews/summarize", productId)
                .exchange()
    }

    WebTestClient.ResponseSpec autocomplete(String query, Integer limit = null) {
        return webTestClient.get()
                .uri(uriBuilder -> {
                    def builder = uriBuilder.path("/products/autocomplete")
                            .queryParam("q", query)
                    if (limit != null) {
                        builder.queryParam("limit", limit)
                    }
                    builder.build()
                })
                .exchange()
    }

    WebTestClient.ResponseSpec createCategorySchema(CategorySchemaCreateRequestDto requestDto) {
        return webTestClient.post()
                .uri("/category-schemas")
                .bodyValue(requestDto)
                .exchange()
    }

    WebTestClient.ResponseSpec getCategorySchema(String category) {
        return webTestClient.get()
                .uri("/category-schemas/{category}", category)
                .exchange()
    }

    WebTestClient.ResponseSpec searchProducts(ProductSearchRequest request) {
        return webTestClient.post()
                .uri("/products/search")
                .bodyValue(request)
                .exchange()
    }

    WebTestClient.ResponseSpec findSimilar(Long id, Integer limit = null) {
        return webTestClient.get()
                .uri(uriBuilder -> {
                    def builder = uriBuilder.pathSegment("products", id.toString(), "similar")
                    if (limit != null) {
                        builder.queryParam("limit", limit)
                    }
                    builder.build()
                })
                .exchange()
    }

    void waitForJobCompletion(WebTestClient.ResponseSpec response, Long jobId = null) {
        def id = jobId ?: response
                .expectStatus().isAccepted()
                .expectBody(Map).returnResult()
                .responseBody.id as String

        def conditions = new PollingConditions(timeout: 30, delay: 0.5)

        conditions.eventually {
            def status = webTestClient.get()
                    .uri("/jobs/${id}")
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(Map).returnResult()
                    .responseBody.status as String

            assert status == "COMPLETED" || status == "FAILED"
        }
    }
}
