package org.skroutz.scraper.skroutzwebscraper.service

import ch.qos.logback.classic.Level
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.skroutz.scraper.skroutzwebscraper.base.WithLoggingBaseSpec
import org.skroutz.scraper.skroutzwebscraper.entity.CategorySchema
import org.skroutz.scraper.skroutzwebscraper.entity.Product
import org.skroutz.scraper.skroutzwebscraper.repository.CategorySchemaRepository
import org.skroutz.scraper.skroutzwebscraper.repository.ProductRepository
import org.skroutz.scraper.skroutzwebscraper.schema.CategoryMappingSchema
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
    SpecsNormalizerService specsNormalizerService = Mock(SpecsNormalizerService)
    CategorySchemaRepository categorySchemaRepository = Mock(CategorySchemaRepository)

    @Subject
    SpecificationsService service = new SpecificationsService(productRepository, specificationsScraper, productSearchService, specsNormalizerService, categorySchemaRepository, 10, 0)

    def "Happy path, should call specificationsScraper.scrapeSpecifications()"() {
        given: "a product object"
            Product product = new Product(title: "Product 1", price: 100, url: "http://example.com/product1", category: "MOBILE_PHONES")

        and: "we have a json node to return"
            ObjectMapper mapper = new ObjectMapper()
            JsonNode jsonNode = mapper.readTree("{\"key\":\"value\"}")
            JsonNode appliedJsonNode = mapper.readTree("{\"key\":\"normalized_value\"}")

        and: "a page with the product"
            Pageable pageable = PageRequest.of(0, 100)
            Page<Product> page = new PageImpl<>([product], pageable, 1)
            Page<Product> emptyPage = new PageImpl<>([], pageable, 0)

        and: "a category schema for the product category"
            CategorySchema categorySchema = CategorySchema.builder()
                .category("MOBILE_PHONES")
                .version(1)
                .schema(new CategoryMappingSchema())
                .build()

        when: "parsing specifications for the product"
            service.parseSpecifications()

        then: "specifications should be scraped from the product URL"
            1 * categorySchemaRepository.findAll() >> [categorySchema]
            1 * productRepository.findAllBySpecificationsIsNullAndSpecificationsSkippedIsFalseOrderByIdAsc(_ as Pageable) >> page
            1 * specificationsScraper.scrapeSpecifications(product.url + "?lang=en") >> Optional.of(jsonNode)
            1 * specsNormalizerService.normalize(jsonNode, categorySchema.schema) >> appliedJsonNode.toString()
            1 * productRepository.saveAll({ List<Product> products ->
                products.size() == 1 &&
                products[0].specifications == jsonNode &&
                products[0].elasticSearchSpecifications == appliedJsonNode &&
                products[0].specificationsSkipped == false
            })
            1 * productSearchService.indexProducts({ List<Product> products ->
                products.size() == 1 &&
                products[0].specifications == jsonNode &&
                products[0].elasticSearchSpecifications == appliedJsonNode
            })
            1 * productRepository.findAllBySpecificationsIsNullAndSpecificationsSkippedIsFalseOrderByIdAsc(_ as Pageable) >> emptyPage
            0 * _
    }

    def "Happy path, should mark product as skipped with URL #required_url"() {
        given: "a product object with no URL"
            Product product = new Product(title: "Product 1", price: 100, url: required_url, id: 1, category: "MOBILE_PHONES")

        and: "a page with the product"
            Pageable pageable = PageRequest.of(0, 100)
            Page<Product> page = new PageImpl<>([product], pageable, 1)
            Page<Product> emptyPage = new PageImpl<>([], pageable, 0)

        and: "a category schema for the product category"
            CategorySchema categorySchema = CategorySchema.builder()
                    .category("MOBILE_PHONES")
                    .version(1)
                    .schema(new CategoryMappingSchema())
                    .build()

        when: "parsing specifications for the product"
            service.parseSpecifications()

        then: "product is marked as skipped but specifications remain null"
        1 * categorySchemaRepository.findAll() >> [categorySchema]
        1 * productRepository.findAllBySpecificationsIsNullAndSpecificationsSkippedIsFalseOrderByIdAsc(_ as Pageable) >> page
            1 * productRepository.saveAll({ List<Product> products ->
                products.size() == 1 &&
                products[0].specifications == null &&
                products[0].elasticSearchSpecifications == null &&
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

        and: "a category schema for the product category"
            CategorySchema categorySchema = CategorySchema.builder()
                    .category("MOBILE_PHONES")
                    .version(1)
                    .schema(new CategoryMappingSchema())
                    .build()

        when: "parsing specifications for the product"
            service.parseSpecifications()

        then: "product is marked as skipped but specifications remain null"
            1 * categorySchemaRepository.findAll() >> [categorySchema]
            1 * productRepository.findAllBySpecificationsIsNullAndSpecificationsSkippedIsFalseOrderByIdAsc(_ as Pageable) >> page
            1 * specificationsScraper.scrapeSpecifications(product.url+ "?lang=en") >> { throw new RuntimeException("Scraping error") }
            1 * productRepository.saveAll({ List<Product> products ->
                products.size() == 1 &&
                products[0].specifications == null &&
                products[0].elasticSearchSpecifications == null &&
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

        and: "a category schema for the product category"
            CategorySchema categorySchema = CategorySchema.builder()
                    .category("MOBILE_PHONES")
                    .version(1)
                    .schema(new CategoryMappingSchema())
                    .build()

        when: "parsing specifications for the product"
            service.parseSpecifications()

        then: "product is marked as skipped but specifications remain null"
            1 * categorySchemaRepository.findAll() >> [categorySchema]
            1 * productRepository.findAllBySpecificationsIsNullAndSpecificationsSkippedIsFalseOrderByIdAsc(_ as Pageable) >> page
            1 * specificationsScraper.scrapeSpecifications(product.url + "?lang=en") >> specifications
            1 * productRepository.saveAll({ List<Product> products ->
                products.size() == 1 &&
                products[0].specifications == null &&
                products[0].elasticSearchSpecifications == null &&
                products[0].specificationsSkipped == true
            })
            1 * productRepository.findAllBySpecificationsIsNullAndSpecificationsSkippedIsFalseOrderByIdAsc(_ as Pageable) >> emptyPage
            0 * _

        and: "log should contain warning about no specifications"
            assertLog(Level.WARN, "No specifications found for product: 1")

        where:
            scenario | specifications
            "null"   | Optional.empty()
            "empty"  | Optional.of(new ObjectMapper().readTree('{}'))
    }

    def "Happy path, should process multiple products in batch"() {
        given: "multiple product objects"
            Product product1 = new Product(title: "Product 1", price: 100, url: "http://example.com/product1", category: "MOBILE_PHONES")
            Product product2 = new Product(title: "Product 2", price: 200, url: "http://example.com/product2", category: "MOBILE_PHONES")

        and: "we have json nodes to return"
            ObjectMapper mapper = new ObjectMapper()
            JsonNode jsonNode1 = mapper.readTree('{"key":"value1"}')
            JsonNode jsonNode2 = mapper.readTree('{"key":"value2"}')
            JsonNode appliedJsonNode1 = mapper.readTree('{"key":"normalized_value1"}')
            JsonNode appliedJsonNode2 = mapper.readTree('{"key":"normalized_value2"}')

        and: "a page with both products"
            Pageable pageable = PageRequest.of(0, 100)
            Page<Product> page = new PageImpl<>([product1, product2], pageable, 2)
            Page<Product> emptyPage = new PageImpl<>([], pageable, 0)

        and: "a category schema for the products category"
            CategorySchema categorySchema = CategorySchema.builder()
                    .category("MOBILE_PHONES")
                    .version(1)
                    .schema(new CategoryMappingSchema())
                    .build()

        when: "parsing specifications for the products"
            service.parseSpecifications()

        then: "specifications should be scraped from both product URLs"
            1 * categorySchemaRepository.findAll() >> [categorySchema]
            1 * productRepository.findAllBySpecificationsIsNullAndSpecificationsSkippedIsFalseOrderByIdAsc(_ as Pageable) >> page
            1 * specificationsScraper.scrapeSpecifications(product1.url + "?lang=en") >> Optional.of(jsonNode1)
            1 * specsNormalizerService.normalize(jsonNode1, categorySchema.schema) >> appliedJsonNode1.toString()
            1 * specificationsScraper.scrapeSpecifications(product2.url + "?lang=en") >> Optional.of(jsonNode2)
            1 * specsNormalizerService.normalize(jsonNode2, categorySchema.schema) >> appliedJsonNode2.toString()
            1 * productRepository.saveAll({ List<Product> products ->
                products.size() == 2 &&
                products[0].specifications == jsonNode1 &&
                products[0].elasticSearchSpecifications == appliedJsonNode1 &&
                products[0].specificationsSkipped == false &&
                products[1].specifications == jsonNode2 &&
                products[1].elasticSearchSpecifications == appliedJsonNode2 &&
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

        and: "a category schema (though it won't be used)"
            CategorySchema categorySchema = CategorySchema.builder()
                    .category("MOBILE_PHONES")
                    .version(1)
                    .schema(new CategoryMappingSchema())
                    .build()

        when: "parsing specifications with no products"
            service.parseSpecifications()

        then: "no processing should occur"
            1 * categorySchemaRepository.findAll() >> [categorySchema]
            1 * productRepository.findAllBySpecificationsIsNullAndSpecificationsSkippedIsFalseOrderByIdAsc(_ as Pageable) >> emptyPage
            0 * _

        and: "log should contain completion message with zero counts"
            assertLog(Level.INFO, "Completed specifications parsing. Total processed: 0, Total successful: 0")
    }

    def "Unhappy path, does not save applied schema if category schema does not exist for category"(){
        given: "a product object"
            Product product = new Product(title: "Product 1", price: 100, url: "http://example.com/product1", category: "MOBILE_PHONES")

        and: "we have a json node to return"
            ObjectMapper mapper = new ObjectMapper()
            JsonNode jsonNode = mapper.readTree("{\"key\":\"value\"}")

        and: "a page with the product"
            Pageable pageable = PageRequest.of(0, 100)
            Page<Product> page = new PageImpl<>([product], pageable, 1)
            Page<Product> emptyPage = new PageImpl<>([], pageable, 0)

        and: "no category schema for the product category"


        when: "parsing specifications for the product"
            service.parseSpecifications()

        then: "specifications should be scraped but not normalized or indexed"
            1 * categorySchemaRepository.findAll() >> []
            1 * productRepository.findAllBySpecificationsIsNullAndSpecificationsSkippedIsFalseOrderByIdAsc(_ as Pageable) >> page
            1 * specificationsScraper.scrapeSpecifications(product.url + "?lang=en") >> Optional.of(jsonNode)
            1 * productRepository.saveAll({ List<Product> products ->
                products.size() == 1 &&
                products[0].specifications == jsonNode &&
                products[0].elasticSearchSpecifications == null &&
                products[0].specificationsSkipped == false
            })
            1 * productSearchService.indexProducts({ List<Product> products ->
                products.size() == 1 &&
                products[0].specifications == jsonNode &&
                products[0].elasticSearchSpecifications == null
            })
            1 * productRepository.findAllBySpecificationsIsNullAndSpecificationsSkippedIsFalseOrderByIdAsc(_ as Pageable) >> emptyPage
            0 * _
    }

    def "Unhappy path, should log error and skip product if normalization throws exception"() {
        given: "a product object"
            Product product = new Product(title: "Product 1", price: 100, url: "http://example.com/product1", category: "MOBILE_PHONES")

        and: "we have a json node to return"
            ObjectMapper mapper = new ObjectMapper()
            JsonNode jsonNode = mapper.readTree("{\"key\":\"value\"}")

        and: "a page with the product"
            Pageable pageable = PageRequest.of(0, 100)
            Page<Product> page = new PageImpl<>([product], pageable, 1)
            Page<Product> emptyPage = new PageImpl<>([], pageable, 0)

        and: "a category schema for the product category"
            CategorySchema categorySchema = CategorySchema.builder()
                    .category("MOBILE_PHONES")
                    .version(1)
                    .schema(new CategoryMappingSchema())
                    .build()

        when: "parsing specifications for the product"
            service.parseSpecifications()

        then: "product is marked as skipped but specifications remain null"
            1 * categorySchemaRepository.findAll() >> [categorySchema]
            1 * productRepository.findAllBySpecificationsIsNullAndSpecificationsSkippedIsFalseOrderByIdAsc(_ as Pageable) >> page
            1 * specificationsScraper.scrapeSpecifications(product.url + "?lang=en") >> Optional.of(jsonNode)
            1 * specsNormalizerService.normalize(jsonNode, categorySchema.schema) >> { throw new RuntimeException("Normalization error") }
            1 * productRepository.saveAll({ List<Product> products ->
                products.size() == 1 &&
                        products[0].specifications == jsonNode &&
                        products[0].elasticSearchSpecifications == null &&
                        products[0].specificationsSkipped == false
            })
            1 * productSearchService.indexProducts({ List<Product> products ->
                products.size() == 1 &&
                        products[0].specifications == jsonNode &&
                        products[0].elasticSearchSpecifications == null
            })
            1 * productRepository.findAllBySpecificationsIsNullAndSpecificationsSkippedIsFalseOrderByIdAsc(_ as Pageable) >> emptyPage
            0 * _
    }
}
