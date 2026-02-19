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
    ProductSearchService productSearchService = Mock(ProductSearchService)

    @Subject
    SpecificationsService service = new SpecificationsService(productRepository, specificationsScraper, productSearchService)

    def "Happy path, should call specificationsScraper.scrapeSpecifications()"() {
        given: "a product object"
            Product product = new Product(title: "Product 1", price: 100, url: "http://example.com/product1")

        and: "we have a json node to return"
            ObjectMapper mapper = new ObjectMapper()
            JsonNode jsonNode = mapper.readTree('{"key":"value"}')

        when: "parsing specifications for the product"
            service.parseSpecifications()

        then: "specifications should be scraped from the product URL"
            1 * productRepository.findAllBySpecificationsParsed(false) >> [product]
            1 * specificationsScraper.scrapeSpecifications(product.url) >> jsonNode
            1 * productRepository.saveAll({ List<Product> products ->
                products.size() == 1 &&
                products[0].specifications == jsonNode &&
                products[0].specificationsParsed == true
            })
            1 * productSearchService.indexProducts({ List<Product> products ->
                products.size() == 1 &&
                products[0].specifications == jsonNode
            })
            0 * _
    }

    def "Happy path, should do nothing if a product has URL #required_url"() {
        given: "a product object with no URL"
            Product product = new Product(title: "Product 1", price: 100, url: required_url, id: 1)

        when: "parsing specifications for the product"
            service.parseSpecifications()

        then: "no interactions should occur"
            1 * productRepository.findAllBySpecificationsParsed(false) >> [product]
            0 * _

        and: "log should contain warning about missing URL"
            assertLog(Level.WARN, "Product URL is empty or null for product: 1")

        where:
            required_url << [null, ""]
    }

    def "Unhappy path, should log error if specificationsScraper throws exception"() {
        given: "a product object"
            Product product = new Product(title: "Product 1", price: 100, url: "http://example.com/product1", id: 1)

        when: "parsing specifications for the product"
            service.parseSpecifications()

        then: "an exception is thrown during scraping"
            1 * productRepository.findAllBySpecificationsParsed(false) >> [product]
            1 * specificationsScraper.scrapeSpecifications(product.url) >> { throw new RuntimeException("Scraping error") }
            0 * _

        and: "log should contain error about scraping failure"
            assertLog(Level.ERROR, "Error parsing specifications for product 1")
    }

    def "Happy path, should skip product indexing if specifications are #scenario"() {
        given: "a product object"
            Product product = new Product(title: "Product 1", price: 100, url: "http://example.com/product1", id: 1)

        when: "parsing specifications for the product"
            service.parseSpecifications()

        then: "specifications scraper returns #scenario"
            1 * productRepository.findAllBySpecificationsParsed(false) >> [product]
            1 * specificationsScraper.scrapeSpecifications(product.url) >> specifications
            0 * _

        and: "log should contain warning about no specifications"
            assertLog(Level.WARN, "No specifications found for product: 1")

        where:
            scenario | specifications
            "null"   | null
            "empty"  | new ObjectMapper().readTree('{}')
    }

    def "Happy path, should process multiple products in batch"() {
        given: "multiple product objects"
            Product product1 = new Product(title: "Product 1", price: 100, url: "http://example.com/product1")
            Product product2 = new Product(title: "Product 2", price: 200, url: "http://example.com/product2")

        and: "we have json nodes to return"
            ObjectMapper mapper = new ObjectMapper()
            JsonNode jsonNode1 = mapper.readTree('{"key":"value1"}')
            JsonNode jsonNode2 = mapper.readTree('{"key":"value2"}')

        when: "parsing specifications for the products"
            service.parseSpecifications()

        then: "specifications should be scraped from both product URLs"
            1 * productRepository.findAllBySpecificationsParsed(false) >> [product1, product2]
            1 * specificationsScraper.scrapeSpecifications(product1.url) >> jsonNode1
            1 * specificationsScraper.scrapeSpecifications(product2.url) >> jsonNode2
            1 * productRepository.saveAll({ List<Product> products ->
                products.size() == 2 &&
                products[0].specifications == jsonNode1 &&
                products[1].specifications == jsonNode2
            })
            1 * productSearchService.indexProducts({ List<Product> products -> products.size() == 2 })
            0 * _
    }
}
