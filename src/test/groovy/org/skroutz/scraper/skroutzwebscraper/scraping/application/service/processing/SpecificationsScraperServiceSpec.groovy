package org.skroutz.scraper.skroutzwebscraper.scraping.application.service.processing

import ch.qos.logback.classic.Level
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.skroutz.scraper.skroutzwebscraper.base.WithLoggingBaseSpec
import org.skroutz.scraper.skroutzwebscraper.category.domain.entity.CategorySchema
import org.skroutz.scraper.skroutzwebscraper.product.domain.entity.Product
import org.skroutz.scraper.skroutzwebscraper.category.domain.schema.CategoryMappingSchema
import org.skroutz.scraper.skroutzwebscraper.scraping.infrastructure.scraper.SpecificationsScraper
import org.skroutz.scraper.skroutzwebscraper.scraping.application.service.processing.SpecificationsScraperService
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
            Product product = new Product(title: "Product 1", price: 100, url: required_url, id: 1, category: "MOBILE_PHONES")
            List<Product> productList = List.of(product)

        and: "a category schema for the product category"
            CategorySchema categorySchema = CategorySchema.builder()
                    .category("MOBILE_PHONES")
                    .version(1)
                    .schema(new CategoryMappingSchema())
                    .build()
            Map<String, CategorySchema> schemas = Map.of("MOBILE_PHONES", categorySchema)

        when:
            def result = service.scrapeBatch(productList, schemas)

        then: "Result is not successful"
            !result[0].isSuccess()
            result[0].rawSpecs() is null
            result[0].normalizedSpecs() is null
            result[0].productId() == product.id

        and: "log should contain warning about missing URL"
            assertLog(Level.WARN, "Product URL is empty or null for product: 1")

        where:
            required_url << [null, ""]
    }

    def "Unhappy path, should mark product as unsuccessful if scraper throws exception"() {
        given: "a product object with no URL"
            Product product = new Product(title: "Product 1", price: 100, url: "http://example.com/product1", id: 1, category: "MOBILE_PHONES")
            List<Product> productList = List.of(product)

        and: "a category schema for the product category"
            CategorySchema categorySchema = CategorySchema.builder()
                    .category("MOBILE_PHONES")
                    .version(1)
                    .schema(new CategoryMappingSchema())
                    .build()
            Map<String, CategorySchema> schemas = Map.of("MOBILE_PHONES", categorySchema)

        and: "Mock scraper behaviour"
           specificationsScraper.scrapeSpecifications(product.url+ "?lang=en") >> { throw new RuntimeException("Scraping error") }

        when:
            def result = service.scrapeBatch(productList, schemas)

        then: "Result is not successful"
            !result[0].isSuccess()
            result[0].rawSpecs() is null
            result[0].normalizedSpecs() is null
            result[0].productId() == product.id

        and: "log should contain error about scraping failure"
            assertLog(Level.ERROR, "Error parsing specifications for product 1")
    }

    @Unroll
    def "Unhappy path, should mark product as unsuccessful if specifications are #scenario"() {
        given: "a product object with no URL"
            Product product = new Product(title: "Product 1", price: 100, url: "http://example.com/product1", id: 1, category: "MOBILE_PHONES")
            List<Product> productList = List.of(product)

        and: "a category schema for the product category"
            CategorySchema categorySchema = CategorySchema.builder()
                    .category("MOBILE_PHONES")
                    .version(1)
                    .schema(new CategoryMappingSchema())
                    .build()
            Map<String, CategorySchema> schemas = Map.of("MOBILE_PHONES", categorySchema)

        and: "Mock scraper behaviour"
            specificationsScraper.scrapeSpecifications(product.url + "?lang=en") >> specifications

        when:
            def result = service.scrapeBatch(productList, schemas)

        then: "Result is not successful"
            !result[0].isSuccess()
            result[0].rawSpecs() is null
            result[0].normalizedSpecs() is null
            result[0].productId() == product.id

        and: "log should contain warning about no specifications"
            assertLog(Level.WARN, "No specifications found for product: 1")

        where:
            scenario | specifications
            "null"   | Optional.empty()
            "empty"  | Optional.of(new ObjectMapper().readTree('{}'))
    }

    def "Unhappy path, does not apply applied schema if category schema does not exist for category"() {
        given: "a product object with no URL"
            Product product = new Product(title: "Product 1", price: 100, url: "http://example.com/product1", id: 1, category: "UNKNOWN")
            List<Product> productList = List.of(product)

        and: "we have a json node to return"
            ObjectMapper mapper = new ObjectMapper()
            JsonNode jsonNode = mapper.readTree("{\"key\":\"value\"}")

        and: "Mock scraper behaviour"
            specificationsScraper.scrapeSpecifications(product.url + "?lang=en") >> Optional.of(jsonNode)

        when:
            def result = service.scrapeBatch(productList, Map.of())

        then: "Result is successful, but without applied schema"
            result[0].isSuccess()
            result[0].rawSpecs() is jsonNode
            result[0].normalizedSpecs() is null
            result[0].productId() == product.id

        and: "Assert log"
            assertLog(Level.WARN, "No schema found for category 'UNKNOWN'")
    }

    def "Unhappy path, does not apply applied schema if normalization throws exception"() {
        given: "a product object with no URL"
            Product product = new Product(title: "Product 1", price: 100, url: "http://example.com/product1", id: 1, category: "MOBILE_PHONES")
            List<Product> productList = List.of(product)

        and: "a category schema for the product category"
            CategorySchema categorySchema = CategorySchema.builder()
                    .category("MOBILE_PHONES")
                    .version(1)
                    .schema(new CategoryMappingSchema())
                    .build()
            Map<String, CategorySchema> schemas = Map.of("MOBILE_PHONES", categorySchema)

        and: "we have a json node to return"
            ObjectMapper mapper = new ObjectMapper()
            JsonNode jsonNode = mapper.readTree("{\"key\":\"value\"}")

        and: "Mock scraper behaviour"
            specificationsScraper.scrapeSpecifications(product.url + "?lang=en") >> Optional.of(jsonNode)

        and: "Mock normalization to throw exception for category"
            specificationsNormalizerUtils.normalize(jsonNode, categorySchema.schema) >> { throw new RuntimeException("Normalization error") }

        when:
            def result = service.scrapeBatch(productList, schemas)

        then: "Result is successful, but without applied schema"
            result[0].isSuccess()
            result[0].rawSpecs() is jsonNode
            result[0].normalizedSpecs() is null
            result[0].productId() == product.id

        and: "Assert log"
            assertLog(Level.WARN, "Normalization failed for product 1")
    }
}
