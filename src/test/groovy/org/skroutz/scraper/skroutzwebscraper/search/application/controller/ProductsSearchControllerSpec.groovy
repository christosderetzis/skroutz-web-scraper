package org.skroutz.scraper.skroutzwebscraper.search.application.controller

import org.skroutz.scraper.skroutzwebscraper.common.exception.RestResponseEntityExceptionHandler
import org.skroutz.scraper.skroutzwebscraper.search.application.service.ProductSearchService
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.servlet.OAuth2ResourceServerAutoConfiguration
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
@ContextConfiguration(classes = [ProductSearchController, RestResponseEntityExceptionHandler])
@ImportAutoConfiguration(exclude = OAuth2ResourceServerAutoConfiguration)
class ProductsSearchControllerSpec extends Specification {

    @Autowired
    MockMvc mockMvc

    @SpringBean
    ProductSearchService productSearchService = Mock(ProductSearchService)

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
