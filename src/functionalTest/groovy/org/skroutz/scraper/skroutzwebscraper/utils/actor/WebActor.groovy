package org.skroutz.scraper.skroutzwebscraper.utils.actor

import org.skroutz.scraper.skroutzwebscraper.dto.ScraperRequestDto
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

    WebTestClient.ResponseSpec scrapePriceHistory() {
        return webTestClient.post()
                .uri("/scraper/price-history")
                .exchange()
    }
}
