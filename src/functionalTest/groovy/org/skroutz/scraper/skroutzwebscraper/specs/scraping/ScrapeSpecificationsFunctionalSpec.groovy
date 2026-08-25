package org.skroutz.scraper.skroutzwebscraper.specs.scraping

import com.fasterxml.jackson.databind.JsonNode
import org.skroutz.scraper.skroutzwebscraper.category.domain.entity.CategorySchema
import org.skroutz.scraper.skroutzwebscraper.product.domain.entity.Product
import org.skroutz.scraper.skroutzwebscraper.category.domain.schema.CategoryMappingSchema
import org.skroutz.scraper.skroutzwebscraper.utils.base.BaseFunctionalSpec
import org.skroutz.scraper.skroutzwebscraper.utils.helpers.JsonFileReader
import org.skyscreamer.jsonassert.JSONAssert
import org.skyscreamer.jsonassert.JSONCompareMode

class ScrapeSpecificationsFunctionalSpec extends BaseFunctionalSpec {

    def "Scrape specifications, happy path"() {
        given: "an existing product with a valid URL"
            def productUrl = "http://localhost:8080/product-details.html"
            def productTitle = "Test Product with Specifications"
            def productPrice = 150.0
            Product product = new Product()
            product.title = productTitle
            product.price = productPrice
            product.url = productUrl
            product.category = "MOBILE_PHONES"
            product = productRepository.save(product)

        and: "an expected category schema for the product category"
            JsonNode categorySchema = JsonFileReader.readJsonFromResource("category-schema.json")
            CategoryMappingSchema existingSchema = objectMapper.readValue(categorySchema.toString(), CategoryMappingSchema.class)
            categorySchemaRepository.saveAndFlush(
                    CategorySchema.builder()
                            .category("MOBILE_PHONES")
                            .version(1)
                            .schema(existingSchema)
                            .build()
            )

        when:
            def response = webActor.scrapeSpecifications()
            webActor.waitForJobCompletion(response)

        then:
            Product savedProduct = productRepository.findById(product.id).orElse(null)
            JsonNode expectedRawSpecs = JsonFileReader.readJsonFromResource("expected-specs.json")
            JsonNode expectedNormalizedSpecs = JsonFileReader.readJsonFromResource("expected-applied-specs.json")
            with(savedProduct) {
                JSONAssert.assertEquals(expectedRawSpecs.toString(), specifications.toString(), JSONCompareMode.NON_EXTENSIBLE)
                JSONAssert.assertEquals(expectedNormalizedSpecs.toString(), elasticSearchSpecifications.toString(), JSONCompareMode.NON_EXTENSIBLE)
                brand == "Apple"
            }

        and: "Product is indexed in elasticsearch"
            def productDocuments = productElasticsearchRepository.findAll()
            assert productDocuments.size() == 1

        and: "ElasticSearch specifications match the expected normalized schema"
            JsonNode elasticSearchSpecs = objectMapper.convertValue(productDocuments[0].specifications, JsonNode.class)
            assert elasticSearchSpecs == expectedNormalizedSpecs
    }

    def "Scrape specifications, blank url"() {
        given:
            Product product = new Product()
            product.title = "Test Product No Specifications"
            product.price = 100.0
            product.url = ""
            product = productRepository.save(product)

        when:
            def response = webActor.scrapeSpecifications()
            webActor.waitForJobCompletion(response)

        then:
            Product savedProduct = productRepository.findById(product.id).orElse(null)
            with(savedProduct) {
                specifications == null
                specificationsSkipped == true
                brand == null
            }
    }
}
