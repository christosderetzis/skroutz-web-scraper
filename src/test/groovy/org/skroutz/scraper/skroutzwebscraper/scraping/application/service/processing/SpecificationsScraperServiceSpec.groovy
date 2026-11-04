package org.skroutz.scraper.skroutzwebscraper.scraping.application.service.processing

import ch.qos.logback.classic.Level
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.skroutz.scraper.skroutzwebscraper.base.WithLoggingBaseSpec
import org.skroutz.scraper.skroutzwebscraper.common.infrastructure.dto.events.SpecificationsScrapeResult
import org.skroutz.scraper.skroutzwebscraper.product.ProductDto
import org.skroutz.scraper.skroutzwebscraper.category.domain.schema.CategoryMappingSchema
import org.skroutz.scraper.skroutzwebscraper.scraping.infrastructure.scraper.SpecificationsScraper
import org.skroutz.scraper.skroutzwebscraper.scraping.infrastructure.utils.SpecificationsNormalizerUtils
import spock.lang.Subject
import spock.lang.Unroll

class SpecificationsScraperServiceSpec extends WithLoggingBaseSpec {

    SpecificationsScraper specificationsScraper = Mock(SpecificationsScraper)
    SpecificationsNormalizerUtils specificationsNormalizerUtils = Mock(SpecificationsNormalizerUtils)

    @Subject
    SpecificationsScraperService service

    def setup() {
        service = new SpecificationsScraperService(specificationsScraper, specificationsNormalizerUtils)
        service.specificationsDelayMs = 0
    }

    @Unroll
    def "Happy path, should mark product as skipped with URL #required_url"() {
        given: "a product object with no URL"
            ProductDto product = new ProductDto(100, required_url, "iphone 18", "256gb storage", "MOBILE_PHONES")
            List<ProductDto> productList = List.of(product)

        and: "a category schema for the product category"
            Map<String, CategoryMappingSchema> schemas = Map.of("MOBILE_PHONES", new CategoryMappingSchema())

        when:
            def result = service.scrapeBatch(productList, schemas)

        then: "Result is not successful"
            !result.items()[0].isSuccess()
            result.items()[0].rawSpecs() is null
            result.items()[0].normalizedSpecs() is null
            result.items()[0].productId() == product.id()

        and: "log should contain warning about missing URL"
            assertLog(Level.WARN, "Product URL is empty or null for product: 1")

        where:
            required_url << [null, ""]
    }

    def "Unhappy path, should mark product as unsuccessful if scraper throws exception"() {
        given: "a product object with no URL"
            ProductDto product = new ProductDto(100, "http://example.com/product1", "iphone 18", "256gb storage", "MOBILE_PHONES")
            List<ProductDto> productList = List.of(product)

        and: "a category schema for the product category"
            Map<String, CategoryMappingSchema> schemas = Map.of("MOBILE_PHONES", new CategoryMappingSchema())

        and: "Mock scraper behaviour"
           specificationsScraper.scrapeSpecifications(product.url()+ "?lang=en") >> { throw new RuntimeException("Scraping error") }

        when:
        SpecificationsScrapeResult result = service.scrapeBatch(productList, schemas)

        then: "Result is not successful"
            !result.items()[0].isSuccess()
            result.items()[0].rawSpecs() is null
            result.items()[0].normalizedSpecs() is null
            result.items()[0].productId() == product.id()

        and: "log should contain error about scraping failure"
            assertLog(Level.ERROR, "Error parsing specifications for product 1")
    }

    @Unroll
    def "Unhappy path, should mark product as unsuccessful if specifications are #scenario"() {
        given: "a product object with no URL"
            ProductDto product = randomProduct(1L, "http://example.com/product1")
            List<ProductDto> productList = List.of(product)

        and: "a category schema for the product category"
            Map<String, CategoryMappingSchema> schemas = Map.of("MOBILE_PHONES", new CategoryMappingSchema())

        and: "Mock scraper behaviour"
            specificationsScraper.scrapeSpecifications(product.url() + "?lang=en") >> specifications

        when:
            def result = service.scrapeBatch(productList, schemas)

        then: "Result is not successful"
            !result.items()[0].isSuccess()
            result.items()[0].rawSpecs() is null
            result.items()[0].normalizedSpecs() is null
            result.items()[0].productId() == product.id()

        and: "log should contain warning about no specifications"
            assertLog(Level.WARN, "No specifications found for product: 1")

        where:
            scenario | specifications
            "null"   | Optional.empty()
            "empty"  | Optional.of(new ObjectMapper().readTree('{}'))
    }

    def "Unhappy path, does not apply applied schema if category schema does not exist for category"() {
        given: "a product object with no URL"
            ProductDto product = randomProduct(1L, "http://example.com/product1", "UNKNOWN")
            List<ProductDto> productList = List.of(product)

        and: "we have a json node to return"
            ObjectMapper mapper = new ObjectMapper()
            JsonNode jsonNode = mapper.readTree("{\"key\":\"value\"}")

        and: "Mock scraper behaviour"
            specificationsScraper.scrapeSpecifications(product.url() + "?lang=en") >> Optional.of(jsonNode)

        when:
            def result = service.scrapeBatch(productList, Map.of())

        then: "Result is successful, but without applied schema"
            result.items()[0].isSuccess()
            result.items()[0].rawSpecs() is jsonNode
            result.items()[0].normalizedSpecs() is null
            result.items()[0].productId() == product.id()

        and: "Assert log"
            assertLog(Level.WARN, "No schema found for category 'UNKNOWN'")
    }

    def "Unhappy path, does not apply applied schema if normalization throws exception"() {
        given: "a product object with no URL"
            ProductDto product = randomProduct(1L, "http://example.com/product1")
            List<ProductDto> productList = List.of(product)

        and: "a category schema for the product category"
            Map<String, CategoryMappingSchema> schemas = Map.of("MOBILE_PHONES", new CategoryMappingSchema())

        and: "we have a json node to return"
            ObjectMapper mapper = new ObjectMapper()
            JsonNode jsonNode = mapper.readTree("{\"key\":\"value\"}")

        and: "Mock scraper behaviour"
            specificationsScraper.scrapeSpecifications(product.url() + "?lang=en") >> Optional.of(jsonNode)

        and: "Mock normalization to throw exception for category"
            specificationsNormalizerUtils.normalize(jsonNode, schemas.get("MOBILE_PHONES")) >> { throw new RuntimeException("Normalization error") }

        when:
            def result = service.scrapeBatch(productList, schemas)

        then: "Result is successful, but without applied schema"
            result.items()[0].isSuccess()
            result.items()[0].rawSpecs() is jsonNode
            result.items()[0].normalizedSpecs() is null
            result.items()[0].productId() == product.id()

        and: "Assert log"
            assertLog(Level.WARN, "Normalization failed for product 1")
    }

    ProductDto randomProduct(Long id, String url, String category = "MOBILE_PHONES") {
        new ProductDto(id, url, "iphone 18", "256gb storage, 12gb ram", category)
    }
}
