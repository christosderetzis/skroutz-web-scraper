package org.skroutz.scraper.skroutzwebscraper.controller

import org.skroutz.scraper.skroutzwebscraper.exception.RestResponseEntityExceptionHandler
import org.skroutz.scraper.skroutzwebscraper.dto.ProductDetailsResponseDto
import org.skroutz.scraper.skroutzwebscraper.dto.ProductSuggestionDto
import org.skroutz.scraper.skroutzwebscraper.service.ProductSearchService
import org.skroutz.scraper.skroutzwebscraper.service.ProductsService
import org.spockframework.spring.SpringBean
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.web.servlet.MockMvc
import spock.lang.Specification

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest
@ContextConfiguration(classes = [ProductsController, RestResponseEntityExceptionHandler])
class ProductsControllerSpec extends Specification {

    @Autowired
    MockMvc mockMvc

    @SpringBean
    ProductsService productsService = Mock(ProductsService)

    @SpringBean
    ProductSearchService productSearchService = Mock(ProductSearchService)

    def "should return product details for valid ID"() {
        given: "a valid product ID"
            def productId = 123L
            def productDetails = ProductDetailsResponseDto.builder()
                    .id(productId)
                    .title("Test Product")
                    .url("https://example.com/product/123")
                    .build()

        when: "requesting product details"
            def result = mockMvc.perform(get("/products/{id}", productId))

        then: "should return product details"
            result.andExpect(status().isOk())
            1 * productsService.getProductDetails(productId) >> productDetails
    }

    def "should return autocomplete suggestions for valid query"() {
        given: "a valid search query"
            def query = "laptop"
            def suggestions = [
                    new ProductSuggestionDto(id: 1L, title: "Laptop 1"),
                    new ProductSuggestionDto(id: 2L, title: "Laptop 2")
            ]

        when: "requesting autocomplete suggestions"
            def result = mockMvc.perform(get("/products/autocomplete")
                    .param("q", query)
                    .param("limit", "5"))

        then: "should return suggestions"
            result.andExpect(status().isOk())
            1 * productSearchService.getProductSuggestions(query, 5) >> suggestions
    }

    def "should return 400 when autocomplete validation fails: #scenario"() {
        when: "performing GET request with invalid parameters"
            def request = get("/products/autocomplete")
            if (query != null) {
                request.param("q", query)
            }
            if (limit != null) {
                request.param("limit", limit.toString())
            }
            def result = mockMvc.perform(request)

        then: "should return 400 Bad Request with validation error"
            result.andExpect(status().isBadRequest())
                    .andExpect(jsonPath('$.status').value(400))
                    .andExpect(jsonPath('$.errors[0]').value(expectedError))
            0 * productSearchService.getProductSuggestions(_, _)

        where:
            scenario              | query | limit || expectedError
            "query is blank"      | "  "  | 5     || "q: Search query is required"
            "limit is less than 1"| "test"| 0     || "limit: Limit must be at least 1"
            "limit is negative"   | "test"| -1    || "limit: Limit must be at least 1"
    }
}
