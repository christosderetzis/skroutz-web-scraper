package org.skroutz.scraper.skroutzwebscraper.specs

import org.skroutz.scraper.skroutzwebscraper.processing.dto.ProductDetailsResponseDto
import org.skroutz.scraper.skroutzwebscraper.processing.entity.Product
import org.skroutz.scraper.skroutzwebscraper.utils.base.BaseFunctionalSpec
import org.skroutz.scraper.skroutzwebscraper.utils.creators.ProductCreator
import org.skyscreamer.jsonassert.JSONAssert
import org.skyscreamer.jsonassert.JSONCompareMode

class GetProductByIdFunctionalSpec extends BaseFunctionalSpec {

    def "Happy path - Get product by id"() {
        given: "an existing product"
            Product savedProduct = productRepository.saveAndFlush(ProductCreator.createRandomProduct())

        when: "requesting the product by ID"
            def response = webActor.getProductById(savedProduct.id)

        then: "the response status should be 200 OK and contain product details"
            response.expectStatus().isOk()

        and: "the response body should contain the product details"
            ProductDetailsResponseDto productDetailsResponseDto = response.expectBody(ProductDetailsResponseDto).returnResult().getResponseBody()
            with(productDetailsResponseDto) {
                id == savedProduct.id
                title == savedProduct.title
                url == savedProduct.url
                imageUrl == savedProduct.imageUrl
                price == savedProduct.price
                description == savedProduct.description
            }
    }

    def "Unhappy path - Get product by non-existing id"() {
        given: "a non-existing product ID"
            Long nonExistingProductId = 9999L

        when: "requesting the product by the non-existing ID"
            def response = webActor.getProductById(nonExistingProductId)

        then: "the response status should be 404 Not Found"
            response.expectStatus().isNotFound()

        and: "the response body should contain an error message"
            String responseBody = response.expectBody(String).returnResult().getResponseBody()
            String expectedResponseBody = """
                {
                    "status": 404,
                    "method": "GET",
                    "errors": ["Product not found with id: ${nonExistingProductId}"],
                    "path": "/products/${nonExistingProductId}"
                }
                """
            JSONAssert.assertEquals(expectedResponseBody, responseBody, JSONCompareMode.LENIENT)
    }
}
