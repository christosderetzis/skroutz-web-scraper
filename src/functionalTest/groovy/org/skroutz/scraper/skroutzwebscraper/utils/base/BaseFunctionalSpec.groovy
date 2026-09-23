package org.skroutz.scraper.skroutzwebscraper.utils.base

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.annotation.PostConstruct
import org.skroutz.scraper.skroutzwebscraper.SkroutzWebScraperApplication
import org.skroutz.scraper.skroutzwebscraper.priceHistory.domain.entity.PriceHistory
import org.skroutz.scraper.skroutzwebscraper.scraping.domain.repository.ScrapeJobRepository
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

import java.sql.Timestamp
import java.time.LocalDate
import java.time.ZoneOffset

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = [SkroutzWebScraperApplication, TestWebClientConfig],
        properties = [
                "spring.main.allow-bean-definition-overriding=true",
                "DB_URL=jdbc:postgresql://localhost:5434/skroutz_scraper",
                "ELASTICSEARCH_URL=http://localhost:9201",
                "scraper.base-url=http://localhost:8081",
                "scraper.delay-range-min=100",
                "scraper.delays.review-page-ms=5",
                "scraper.delays.specifications-ms=5",
                "scraper.delays.price-history-ms=5",
                "scraper.delays.reviews-ms=5",
                "scraper.job.stale-threshold-hours=2",
                "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:8083/realms/skroutz-scraper-functional-tests",
                "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:8083/realms/skroutz-scraper-functional-tests/protocol/openid-connect/certs",
                "jwt.auth.converter.resource-id=skroutz-scraper-client-fT"
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

    @Autowired
    ScrapeJobRepository scrapeJobRepository

    @Autowired
    WebActor webActor

    @Shared
    ObjectMapper objectMapper = new ObjectMapper()

    private static final LocalDate LATEST_DATE = LocalDate.of(2026, 9, 1)

    def setup() {
        scrapeJobRepository.deleteAll()
        categorySchemaRepository.deleteAll()
        reviewSummaryRepository.deleteAll()
        priceHistoryRepository.deleteAll()
        reviewRepository.deleteAll()
        productRepository.deleteAll()
        productElasticsearchRepository.deleteAll()
    }

    def cleanup() {
        scrapeJobRepository.deleteAll()
        categorySchemaRepository.deleteAll()
        reviewSummaryRepository.deleteAll()
        priceHistoryRepository.deleteAll()
        reviewRepository.deleteAll()
        productRepository.deleteAll()
        productElasticsearchRepository.deleteAll()
    }

    @PostConstruct
    void init() {
        webActor.setup(port)
    }

    protected Product createAndIndexProduct(String title) {
        return createAndIndexProduct(title, "electronics", "Apple", 999.99.toBigDecimal())
    }

    protected Product createAndIndexProduct(String title, String category, String brand, BigDecimal price) {
        Product product = Product.builder()
                .title(title)
                .url("http://example.com/${title.replaceAll(' ', '-').toLowerCase()}")
                .category(category)
                .brand(brand)
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

    protected Product seedProduct(String title, String urlSlug) {
        return productRepository.saveAndFlush(Product.builder()
                .title(title)
                .url("http://example.com/${urlSlug}")
                .price(100.00)
                .build())
    }

    protected void seedPriceHistory(Product product, List<Map<String, Object>> rows) {
        rows.each { r ->
            priceHistoryRepository.saveAndFlush(PriceHistory.builder()
                    .productId(product.id)
                    .price(r.price.toBigDecimal())
                    .priceDate(Timestamp.from(LATEST_DATE.minusDays(r.daysAgo as int).atStartOfDay().toInstant(ZoneOffset.UTC)))
                    .storeName("TestStore")
                    .build())
        }
    }

    protected static List<Map<String, Object>> rangeRows(int startDaysAgo, int endDaysAgo, BigDecimal price) {
        (startDaysAgo..endDaysAgo).collect { [daysAgo: it, price: price] }
    }

    protected <T> List<T> extractResponseList(WebTestClient.ResponseSpec resp, Class<T> elementType) {
        return resp.expectBody(new ParameterizedTypeReference<List<T>>() {})
                .returnResult()
                .getResponseBody()
    }

    private void waitForElasticsearchRefresh() {
        // Elasticsearch needs time to index documents and make them searchable
        // In tests, we need to wait a bit for the index to refresh
        Thread.sleep(20)
    }
}
