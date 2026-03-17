package org.skroutz.scraper.skroutzwebscraper.specs

import com.fasterxml.jackson.databind.JsonNode
import org.skroutz.scraper.skroutzwebscraper.processing.entity.Product
import org.skroutz.scraper.skroutzwebscraper.utils.base.BaseFunctionalSpec
import org.skroutz.scraper.skroutzwebscraper.utils.helpers.JsonFileReader
import org.skyscreamer.jsonassert.JSONAssert
import org.skyscreamer.jsonassert.JSONCompareMode

class ScrapeSpecificationsFunctionalSpec extends BaseFunctionalSpec {

    def "Scrape specifications, happy path"() {
        given:
            def productUrl = "http://mockserver/product-details.html"
            def productTitle = "Test Product with Specifications"
            def productPrice = 150.0
            Product product = new Product()
            product.title = productTitle
            product.price = productPrice
            product.url = productUrl
            product.specificationsParsed = false
            product = productRepository.save(product)

        when:
            webActor.scrapeSpecifications()

        then:
            Product savedProduct = productRepository.findById(product.id).orElse(null)
            JsonNode expectedJsonNode = JsonFileReader.readJsonFromResource("expected-specs.json")
            with(savedProduct) {
                JSONAssert.assertEquals(expectedJsonNode.toString(), specifications.toString(), JSONCompareMode.NON_EXTENSIBLE)
                specificationsParsed == true
            }
    }

    def "Scrape specifications, blank url"() {
        given:
            Product product = new Product()
            product.title = "Test Product No Specifications"
            product.price = 100.0
            product.url = ""
            product.specificationsParsed = false
            product = productRepository.save(product)

        when:
            webActor.scrapeSpecifications()

        then:
            Product savedProduct = productRepository.findById(product.id).orElse(null)
            with(savedProduct) {
                specifications == null
                specificationsParsed == false
            }
    }
}
