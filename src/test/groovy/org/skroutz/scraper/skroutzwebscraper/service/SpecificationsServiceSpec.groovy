package org.skroutz.scraper.skroutzwebscraper.service

import ch.qos.logback.classic.Level
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.skroutz.scraper.skroutzwebscraper.base.WithLoggingBaseSpec
import org.skroutz.scraper.skroutzwebscraper.entity.Product
import org.skroutz.scraper.skroutzwebscraper.repository.ProductRepository
import org.skroutz.scraper.skroutzwebscraper.scraper.SpecificationsScraper
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import spock.lang.Subject

class SpecificationsServiceSpec extends WithLoggingBaseSpec {
    ProductRepository productRepository = Mock(ProductRepository)
    SpecificationsScraper specificationsScraper = Mock(SpecificationsScraper)
    ProductSearchService productSearchService = Mock(ProductSearchService)

    @Subject
    SpecificationsService service = new SpecificationsService(productRepository, specificationsScraper, productSearchService, 10)

    def "Happy path, should call specificationsScraper.scrapeSpecifications()"() {
        given: "a product object"
            Product product = new Product(title: "Product 1", price: 100, url: "http://example.com/product1")

        and: "we have a json node to return"
            ObjectMapper mapper = new ObjectMapper()
            JsonNode jsonNode = mapper.readTree("{\"key\":\"value\"}")

        and: "a page with the product"
            Pageable pageable = PageRequest.of(0, 100)
            Page<Product> page = new PageImpl<>([product], pageable, 1)
            Page<Product> emptyPage = new PageImpl<>([], pageable, 0)

        when: "parsing specifications for the product"
            service.parseSpecifications()

        then: "specifications should be scraped from the product URL"
            1 * productRepository.findAllBySpecificationsIsNullAndSpecificationsSkippedIsFalseOrderByIdAsc(_ as Pageable) >> page
            1 * specificationsScraper.scrapeSpecifications(product.url + "?lang=en") >> jsonNode
            1 * productRepository.saveAll({ List<Product> products ->
                products.size() == 1 &&
                products[0].specifications == jsonNode &&
                products[0].specificationsSkipped == false
            })
            1 * productSearchService.indexProducts({ List<Product> products ->
                products.size() == 1 &&
                products[0].specifications == jsonNode
            })
            1 * productRepository.findAllBySpecificationsIsNullAndSpecificationsSkippedIsFalseOrderByIdAsc(_ as Pageable) >> emptyPage
            0 * _
    }

    def "Happy path, should mark product as skipped with URL #required_url"() {
        given: "a product object with no URL"
            Product product = new Product(title: "Product 1", price: 100, url: required_url, id: 1)

        and: "a page with the product"
            Pageable pageable = PageRequest.of(0, 100)
            Page<Product> page = new PageImpl<>([product], pageable, 1)
            Page<Product> emptyPage = new PageImpl<>([], pageable, 0)

        when: "parsing specifications for the product"
            service.parseSpecifications()

        then: "product is marked as skipped but specifications remain null"
            1 * productRepository.findAllBySpecificationsIsNullAndSpecificationsSkippedIsFalseOrderByIdAsc(_ as Pageable) >> page
            1 * productRepository.saveAll({ List<Product> products ->
                products.size() == 1 &&
                products[0].specifications == null &&
                products[0].specificationsSkipped == true
            })
            1 * productRepository.findAllBySpecificationsIsNullAndSpecificationsSkippedIsFalseOrderByIdAsc(_ as Pageable) >> emptyPage
            0 * _

        and: "log should contain warning about missing URL"
            assertLog(Level.WARN, "Product URL is empty or null for product: 1")

        where:
            required_url << [null, ""]
    }

    def "Unhappy path, should mark product as skipped if scraper throws exception"() {
        given: "a product object"
            Product product = new Product(title: "Product 1", price: 100, url: "http://example.com/product1", id: 1)

        and: "a page with the product"
            Pageable pageable = PageRequest.of(0, 100)
            Page<Product> page = new PageImpl<>([product], pageable, 1)
            Page<Product> emptyPage = new PageImpl<>([], pageable, 0)

        when: "parsing specifications for the product"
            service.parseSpecifications()

        then: "product is marked as skipped but specifications remain null"
            1 * productRepository.findAllBySpecificationsIsNullAndSpecificationsSkippedIsFalseOrderByIdAsc(_ as Pageable) >> page
            1 * specificationsScraper.scrapeSpecifications(product.url+ "?lang=en") >> { throw new RuntimeException("Scraping error") }
            1 * productRepository.saveAll({ List<Product> products ->
                products.size() == 1 &&
                products[0].specifications == null &&
                products[0].specificationsSkipped == true
            })
            1 * productRepository.findAllBySpecificationsIsNullAndSpecificationsSkippedIsFalseOrderByIdAsc(_ as Pageable) >> emptyPage
            0 * _

        and: "log should contain error about scraping failure"
            assertLog(Level.ERROR, "Error parsing specifications for product 1")
    }

    def "Happy path, should mark product as skipped if specifications are #scenario"() {
        given: "a product object"
            Product product = new Product(title: "Product 1", price: 100, url: "http://example.com/product1", id: 1)

        and: "a page with the product"
            Pageable pageable = PageRequest.of(0, 100)
            Page<Product> page = new PageImpl<>([product], pageable, 1)
            Page<Product> emptyPage = new PageImpl<>([], pageable, 0)

        when: "parsing specifications for the product"
            service.parseSpecifications()

        then: "product is marked as skipped but specifications remain null"
            1 * productRepository.findAllBySpecificationsIsNullAndSpecificationsSkippedIsFalseOrderByIdAsc(_ as Pageable) >> page
            1 * specificationsScraper.scrapeSpecifications(product.url + "?lang=en") >> specifications
            1 * productRepository.saveAll({ List<Product> products ->
                products.size() == 1 &&
                products[0].specifications == null &&
                products[0].specificationsSkipped == true
            })
            1 * productRepository.findAllBySpecificationsIsNullAndSpecificationsSkippedIsFalseOrderByIdAsc(_ as Pageable) >> emptyPage
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

        and: "a page with both products"
            Pageable pageable = PageRequest.of(0, 100)
            Page<Product> page = new PageImpl<>([product1, product2], pageable, 2)
            Page<Product> emptyPage = new PageImpl<>([], pageable, 0)

        when: "parsing specifications for the products"
            service.parseSpecifications()

        then: "specifications should be scraped from both product URLs"
            1 * productRepository.findAllBySpecificationsIsNullAndSpecificationsSkippedIsFalseOrderByIdAsc(_ as Pageable) >> page
            1 * specificationsScraper.scrapeSpecifications(product1.url + "?lang=en") >> jsonNode1
            1 * specificationsScraper.scrapeSpecifications(product2.url + "?lang=en") >> jsonNode2
            1 * productRepository.saveAll({ List<Product> products ->
                products.size() == 2 &&
                products[0].specifications == jsonNode1 &&
                products[0].specificationsSkipped == false &&
                products[1].specifications == jsonNode2 &&
                products[1].specificationsSkipped == false
            })
            1 * productSearchService.indexProducts({ List<Product> products -> products.size() == 2 })
            1 * productRepository.findAllBySpecificationsIsNullAndSpecificationsSkippedIsFalseOrderByIdAsc(_ as Pageable) >> emptyPage
            0 * _
    }

    def "Happy path, should handle empty page gracefully"() {
        given: "an empty page"
            Pageable pageable = PageRequest.of(0, 100)
            Page<Product> emptyPage = new PageImpl<>([], pageable, 0)

        when: "parsing specifications with no products"
            service.parseSpecifications()

        then: "no processing should occur"
            1 * productRepository.findAllBySpecificationsIsNullAndSpecificationsSkippedIsFalseOrderByIdAsc(_ as Pageable) >> emptyPage
            0 * _

        and: "log should contain completion message with zero counts"
            assertLog(Level.INFO, "Completed specifications parsing. Total processed: 0, Total successful: 0")
    }

    def "Happy path, should reset skipped flag on successful retry"() {
        given: "a previously skipped product (after reset)"
            Product product = new Product(title: "Product 1", price: 100, url: "http://example.com/product1")
            product.specificationsSkipped = false  // Reset for retry
            product.specifications = null

        and: "we have a json node to return on retry"
            ObjectMapper mapper = new ObjectMapper()
            JsonNode jsonNode = mapper.readTree('{"key":"value"}')

        and: "a page with the product"
            Pageable pageable = PageRequest.of(0, 100)
            Page<Product> page = new PageImpl<>([product], pageable, 1)
            Page<Product> emptyPage = new PageImpl<>([], pageable, 0)

        when: "parsing specifications for the product"
            service.parseSpecifications()

        then: "specifications should be scraped successfully"
            1 * productRepository.findAllBySpecificationsIsNullAndSpecificationsSkippedIsFalseOrderByIdAsc(_ as Pageable) >> page
            1 * specificationsScraper.scrapeSpecifications(product.url + "?lang=en") >> jsonNode
            1 * productRepository.saveAll({ List<Product> products ->
                products.size() == 1 &&
                products[0].specifications == jsonNode &&
                products[0].specificationsSkipped == false
            })
            1 * productSearchService.indexProducts({ List<Product> products ->
                products.size() == 1 &&
                products[0].specifications == jsonNode
            })
            1 * productRepository.findAllBySpecificationsIsNullAndSpecificationsSkippedIsFalseOrderByIdAsc(_ as Pageable) >> emptyPage
            0 * _
    }
}
