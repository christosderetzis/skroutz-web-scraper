package org.skroutz.scraper.skroutzwebscraper.utils.base

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.annotation.PostConstruct
import org.skroutz.scraper.skroutzwebscraper.repository.ProductRepository
import org.skroutz.scraper.skroutzwebscraper.repository.ReviewRepository
import org.skroutz.scraper.skroutzwebscraper.utils.actor.WebActor
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import spock.lang.Shared
import spock.lang.Specification

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class BaseFunctionalSpec extends Specification {

    @LocalServerPort
    private int port

    @Autowired
    ProductRepository productRepository

    @Autowired
    ReviewRepository reviewRepository

    WebActor webActor

    @Shared
    ObjectMapper objectMapper = new ObjectMapper()

    def setup() {
        reviewRepository.deleteAll()
        productRepository.deleteAll()
    }

    def cleanup() {
        reviewRepository.deleteAll()
        productRepository.deleteAll()
    }

    @PostConstruct
    void init() {
        webActor = new WebActor(port)
    }

    @DynamicPropertySource
    static void setProperties(DynamicPropertyRegistry registry) {
        registry.add("scraper.selenium.url", () -> "http://localhost:4444/wd/hub")
        registry.add("scraper.chromeUserDataDir", () -> "")
        registry.add("spring.datasource.url", () -> "jdbc:postgresql://localhost:5434/skroutz_scraper")
    }
}
