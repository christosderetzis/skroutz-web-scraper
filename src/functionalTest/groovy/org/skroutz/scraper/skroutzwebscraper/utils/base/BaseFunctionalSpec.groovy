package org.skroutz.scraper.skroutzwebscraper.utils.base

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.annotation.PostConstruct
import org.skroutz.scraper.skroutzwebscraper.SkroutzWebScraperApplication
import org.skroutz.scraper.skroutzwebscraper.search.domain.repository.ProductElasticsearchRepository
import org.skroutz.scraper.skroutzwebscraper.search.domain.entity.ProductDocument
import org.skroutz.scraper.skroutzwebscraper.product.domain.entity.Product
import org.skroutz.scraper.skroutzwebscraper.search.infrastructure.mapper.ProductDocumentMapper
import org.skroutz.scraper.skroutzwebscraper.category.domain.repository.CategorySchemaRepository
import org.skroutz.scraper.skroutzwebscraper.priceHistory.domain.repository.PriceHistoryRepository

import org.skroutz.scraper.skroutzwebscraper.product.domain.repository.ProductRepository
import org.skroutz.scraper.skroutzwebscraper.review.domain.repository.ReviewRepository
import org.skroutz.scraper.skroutzwebscraper.review.domain.repository.ReviewSummaryRepository
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
                "DB_URL=jdbc:postgresql://localhost:5434/skroutz_scraper",
                "ELASTICSEARCH_URL=http://localhost:9201",
                "scraper.base-url=http://localhost:8081",
                "scraper.delay-range-min=100",
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
    ReviewSummaryRepository reviewSummaryRepository

    @Autowired
    ProductDocumentMapper productDocumentMapper

    @Autowired
    CategorySchemaRepository categorySchemaRepository

    WebActor webActor

    @Shared
    ObjectMapper objectMapper = new ObjectMapper()

    def setup() {
        categorySchemaRepository.deleteAll()
        reviewSummaryRepository.deleteAll()
        priceHistoryRepository.deleteAll()
        reviewRepository.deleteAll()
        productRepository.deleteAll()
        productElasticsearchRepository.deleteAll()
    }

    def cleanup() {
        categorySchemaRepository.deleteAll()
        reviewSummaryRepository.deleteAll()
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
        return createAndIndexProduct(title, "electronics", 999.99.toBigDecimal())
    }

    protected Product createAndIndexProduct(String title, String category, BigDecimal price) {
        Product product = Product.builder()
                .title(title)
                .url("http://example.com/${title.replaceAll(' ', '-').toLowerCase()}")
                .category(category)
                .price(price)
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
        Thread.sleep(200)
    }
}
