package org.skroutz.scraper.skroutzwebscraper.utils.actor

import org.skroutz.scraper.skroutzwebscraper.category.infrastructure.dto.CategorySchemaCreateRequestDto
import org.skroutz.scraper.skroutzwebscraper.search.infrastructure.dto.ProductSearchRequest
import org.skroutz.scraper.skroutzwebscraper.scraping.infrastructure.dto.ScraperRequestDto
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient

import java.time.Duration

class WebActor {

    WebTestClient webTestClient

    WebActor(int port) {
        this.webTestClient = WebTestClient.bindToServer()
                .baseUrl("http://localhost:${port}")
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .responseTimeout(Duration.ofSeconds(100))
                .build()
    }

    WebTestClient.ResponseSpec scrapeProducts(ScraperRequestDto requestDto, Boolean multiple = false) {
        return webTestClient.post()
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

    WebTestClient.ResponseSpec scrapePriceHistory() {
        return webTestClient.post()
                .uri("/scraper/price-history")
                .exchange()
    }

    WebTestClient.ResponseSpec scrapeSpecifications() {
        return webTestClient.post()
                .uri("/scraper/specifications")
                .exchange()
    }

    WebTestClient.ResponseSpec scrapeReviews() {
        return webTestClient.post()
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
}
