package org.skroutz.scraper.skroutzwebscraper.service

import ch.qos.logback.classic.Level
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.skroutz.scraper.skroutzwebscraper.base.WithLoggingBaseSpec
import org.skroutz.scraper.skroutzwebscraper.entity.Product
import org.skroutz.scraper.skroutzwebscraper.repository.ProductRepository
import org.skroutz.scraper.skroutzwebscraper.scraper.SpecificationsScraper
import spock.lang.Subject

class SpecificationsServiceSpec extends WithLoggingBaseSpec {
    ProductRepository productRepository = Mock(ProductRepository)
    SpecificationsScraper specificationsScraper = Mock(SpecificationsScraper)

    @Subject
    SpecificationsService service = new SpecificationsService(productRepository, specificationsScraper)

    def "Happy path, should call specificationsScraper.screapeSpecifications()"() {
        given: "a product object"
            Product product = new Product(title: "Product 1", price: 100, url: "http://example.com/product1")

        and: "we have a json node to return"
            ObjectMapper mapper = new ObjectMapper()
            JsonNode jsonNode = mapper.readTree("{\"key\":\"value\"}")

        when: "parsing specifications for the product"
            service.parseSpecifications()

        then: "specifications should be scraped from the product URL"
            1 * productRepository.findAllBySpecificationsParsed(false) >> [product]
            1 * specificationsScraper.scrapeSpecifications(product.url + "?lang=en") >> jsonNode
            1 * productRepository.save({ it.specifications == jsonNode && it.specificationsParsed == true })
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

    def "Unhappy path, should log error if specificationsScraper throws exception"() {
        given: "a product object"
            Product product = new Product(title: "Product 1", price: 100, url: "http://example.com/product1", id: 1)

        when: "parsing specifications for the product"
            service.parseSpecifications()

        then: "an exception is thrown during scraping"
            1 * productRepository.findAllBySpecificationsParsed(false) >> [product]
            1 * specificationsScraper.scrapeSpecifications(product.url+ "?lang=en") >> { throw new RuntimeException("Scraping error") }
            0 * _

        and: "log should contain error about scraping failure"
            assertLog(Level.ERROR, "Error parsing specifications for product 1")
    }
}
