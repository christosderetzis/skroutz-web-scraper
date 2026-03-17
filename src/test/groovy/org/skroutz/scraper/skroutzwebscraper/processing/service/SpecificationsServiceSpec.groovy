package org.skroutz.scraper.skroutzwebscraper.processing.service

import ch.qos.logback.classic.Level
import org.skroutz.scraper.skroutzwebscraper.base.WithLoggingBaseSpec
import org.skroutz.scraper.skroutzwebscraper.processing.entity.Product
import org.skroutz.scraper.skroutzwebscraper.processing.repository.ProductRepository
import org.skroutz.scraper.skroutzwebscraper.scraping.ScrapingService
import spock.lang.Subject

class SpecificationsServiceSpec extends WithLoggingBaseSpec {
    ProductRepository productRepository = Mock(ProductRepository)
    ScrapingService scrapingService = Mock(ScrapingService)

    @Subject
    SpecificationsService service = new SpecificationsService(productRepository, scrapingService)

    def "Happy path, should call scrapingService.scrapeSpecifications()"() {
        given: "a product object"
            Product product = new Product(title: "Product 1", price: 100, url: "http://example.com/product1", id: 1L)

        when: "parsing specifications for the product"
            service.parseSpecifications()

        then: "scraping service is called with product id and url"
            1 * productRepository.findAllBySpecificationsParsed(false) >> [product]
            1 * scrapingService.scrapeSpecifications(1L, "http://example.com/product1")
            0 * _
    }

    def "Happy path, should do nothing if a product has no URL"() {
        given: "a product object with no URL"
            Product product = new Product(title: "Product 1", price: 100, url: "", id: 1)

        when: "parsing specifications for the product"
            service.parseSpecifications()

        then: "no interactions should occur"
            1 * productRepository.findAllBySpecificationsParsed(false) >> [product]
            0 * _

        and: "log should contain warning about missing URL"
            assertLog(Level.WARN, "Product URL is empty or null for product: 1")
    }

    def "Unhappy path, should log error if scraping service throws exception"() {
        given: "a product object"
            Product product = new Product(title: "Product 1", price: 100, url: "http://example.com/product1", id: 1)

        when: "parsing specifications for the product"
            service.parseSpecifications()

        then: "an exception is thrown during scraping"
            1 * productRepository.findAllBySpecificationsParsed(false) >> [product]
            1 * scrapingService.scrapeSpecifications(1L, "http://example.com/product1") >> { throw new RuntimeException("Scraping error") }
            0 * _

        and: "log should contain error about scraping failure"
            assertLog(Level.ERROR, "Error parsing specifications for product 1")
    }
}
