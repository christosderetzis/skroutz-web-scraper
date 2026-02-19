package org.skroutz.scraper.skroutzwebscraper.utils.base

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.annotation.PostConstruct
import org.skroutz.scraper.skroutzwebscraper.SkroutzWebScraperApplication
import org.skroutz.scraper.skroutzwebscraper.repository.PriceHistoryRepository
import org.skroutz.scraper.skroutzwebscraper.repository.ProductElasticsearchRepository
import org.skroutz.scraper.skroutzwebscraper.repository.ProductRepository
import org.skroutz.scraper.skroutzwebscraper.repository.ReviewRepository
import org.skroutz.scraper.skroutzwebscraper.utils.actor.WebActor
import org.skroutz.scraper.skroutzwebscraper.utils.config.TestSeleniumConfig
import org.skroutz.scraper.skroutzwebscraper.utils.config.TestWebClientConfig
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import spock.lang.Shared
import spock.lang.Specification

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = [SkroutzWebScraperApplication, TestSeleniumConfig, TestWebClientConfig],
        properties = [
                "spring.main.allow-bean-definition-overriding=true",
                "DB_PORT=5434",
                "ELASTICSEARCH_PORT=9201"
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

    WebActor webActor

    @Shared
    ObjectMapper objectMapper = new ObjectMapper()

    def setup() {
        priceHistoryRepository.deleteAll()
        reviewRepository.deleteAll()
        productRepository.deleteAll()
    }

    def cleanup() {
        priceHistoryRepository.deleteAll()
        reviewRepository.deleteAll()
        productRepository.deleteAll()
    }

    @PostConstruct
    void init() {
        webActor = new WebActor(port)
    }
}
