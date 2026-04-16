package org.skroutz.scraper.skroutzwebscraper.utils.base

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.annotation.PostConstruct
import org.skroutz.scraper.skroutzwebscraper.SkroutzWebScraperApplication
import org.skroutz.scraper.skroutzwebscraper.document.ProductDocument
import org.skroutz.scraper.skroutzwebscraper.entity.Product
import org.skroutz.scraper.skroutzwebscraper.mapper.ProductDocumentMapper
import org.skroutz.scraper.skroutzwebscraper.repository.PriceHistoryRepository
import org.skroutz.scraper.skroutzwebscraper.repository.ProductElasticsearchRepository
import org.skroutz.scraper.skroutzwebscraper.repository.ProductRepository
import org.skroutz.scraper.skroutzwebscraper.repository.ReviewRepository
import org.skroutz.scraper.skroutzwebscraper.utils.actor.WebActor
import org.skroutz.scraper.skroutzwebscraper.utils.config.TestWebClientConfig
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.core.ParameterizedTypeReference
import org.springframework.test.web.reactive.server.WebTestClient
import spock.lang.Shared
import spock.lang.Specification

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = [SkroutzWebScraperApplication, TestWebClientConfig],
        properties = [
                "spring.main.allow-bean-definition-overriding=true",
                "DB_PORT=5434",
                "ELASTICSEARCH_PORT=9201",
                "scraper.base-url=http://localhost:8081",
                "scraper.delay-range-min=100",
                "scraper.selenium.url=http://localhost:4444/wd/hub",
        ]
)
abstract class BaseFunctionalSpec extends Specification {

    @LocalServerPort
    private int port

    @Autowired
    ProductRepository productRepository

    @Autowired
    ReviewRepository reviewRepository

    @Autowired
    PriceHistoryRepository priceHistoryRepository

    @Autowired
    ProductElasticsearchRepository productElasticsearchRepository

    @Autowired
    ProductDocumentMapper productDocumentMapper

    WebActor webActor

    @Shared
    ObjectMapper objectMapper = new ObjectMapper()

    def setup() {
        priceHistoryRepository.deleteAll()
        reviewRepository.deleteAll()
        productRepository.deleteAll()
        productElasticsearchRepository.deleteAll()
    }

    def cleanup() {
        priceHistoryRepository.deleteAll()
        reviewRepository.deleteAll()
        productRepository.deleteAll()
        productElasticsearchRepository.deleteAll()
    }

    @PostConstruct
    void init() {
        webActor = new WebActor(port)
    }

    protected Product createAndIndexProduct(String title) {
        Product product = Product.builder()
                .title(title)
                .url("http://example.com/${title.replaceAll(' ', '-').toLowerCase()}")
                .category("electronics")
                .price(999.99.toBigDecimal())
                .imageUrl("http://example.com/image.jpg")
                .description("Test product")
                .rating(4.5.toBigDecimal())
                .specificationsSkipped(false)
                .reviewsParsed(false)
                .priceHistoryParsed(false)
                .build()

        Product savedProduct = productRepository.saveAndFlush(product)

        ProductDocument document = productDocumentMapper.toDocument(savedProduct)
        productElasticsearchRepository.save(document)

        waitForElasticsearchRefresh()

        return savedProduct
    }

    protected <T> List<T> extractResponseList(WebTestClient.ResponseSpec resp, Class<T> elementType) {
        return resp.expectBody(new ParameterizedTypeReference<List<T>>() {})
                .returnResult()
                .getResponseBody()
    }

    private void waitForElasticsearchRefresh() {
        // Elasticsearch needs time to index documents and make them searchable
        // In tests, we need to wait a bit for the index to refresh
        Thread.sleep(1500)
    }
}
