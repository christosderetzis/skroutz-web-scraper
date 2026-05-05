package org.skroutz.scraper.skroutzwebscraper.controller

import com.fasterxml.jackson.databind.ObjectMapper
import org.skroutz.scraper.skroutzwebscraper.exception.RestResponseEntityExceptionHandler
import org.skroutz.scraper.skroutzwebscraper.dto.ScraperRequestDto
import org.skroutz.scraper.skroutzwebscraper.service.PriceHistoryService
import org.skroutz.scraper.skroutzwebscraper.service.ProductsService
import org.skroutz.scraper.skroutzwebscraper.service.ReviewsService
import org.skroutz.scraper.skroutzwebscraper.service.SpecificationsService
import org.spockframework.spring.SpringBean
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.web.servlet.MockMvc
import spock.lang.Specification

import static org.hamcrest.Matchers.containsInAnyOrder
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest
@ContextConfiguration(classes = [ScraperController, RestResponseEntityExceptionHandler])
class ScraperControllerSpec extends Specification {

    @Autowired
    MockMvc mockMvc

    @SpringBean
    ProductsService productsService = Mock(ProductsService)

    @SpringBean
    ReviewsService reviewsService = Mock(ReviewsService)

    @SpringBean
    SpecificationsService specificationsService = Mock(SpecificationsService)

    @SpringBean
    PriceHistoryService priceHistoryService = Mock(PriceHistoryService)

    ObjectMapper objectMapper = new ObjectMapper()

    def "should scrape single page when multiple is false"() {
        given: "a scraper request for single page"
            def scraperRequestDto = new ScraperRequestDto(url: "https://example.com/products", category: "electronics")
            def requestBody = objectMapper.writeValueAsString(scraperRequestDto)

        when: "performing POST request with multiple=false"
            def result = mockMvc.perform(post("/scraper/products")
                    .param("multiple", "false")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))

        then: "should scrape single page and return OK"
            result.andExpect(status().isOk())
            1 * productsService.scrapeProducts({ it.url == "https://example.com/products" && it.category == "electronics" }, false)
            0 * productsService.getNumberOfWebPages(_)
    }

    def "should scrape multiple pages when multiple is true and pages exist"() {
        given: "a scraper request for multiple pages"
            def scraperRequestDto = new ScraperRequestDto(url: "https://example.com/products", category: "electronics")

        when: "performing POST request with multiple=true"
            def result = mockMvc.perform(post("/scraper/products")
                    .param("multiple", "true")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(scraperRequestDto)))

        then: "should scrape all pages and return OK"
            result.andExpect(status().isOk())
            1 * productsService.scrapeProducts({ it.url == "https://example.com/products" && it.category == "electronics" }, true)
            0 * _
    }

    def "should scrape price history successfully"() {
        when: "performing POST request to scrape price history"
            def result = mockMvc.perform(post("/scraper/price-history"))

        then: "should call price history service and return OK"
            result.andExpect(status().isOk())
            1 * priceHistoryService.fetchPriceHistoryForProducts()
    }

    def "should return 400 when request validation fails: #scenario"() {
        given: "a scraper request with invalid data"
            def scraperRequestDto = new ScraperRequestDto(url: url, category: category)
            def requestBody = objectMapper.writeValueAsString(scraperRequestDto)

        when: "performing POST request"
            def result = mockMvc.perform(post("/scraper/products")
                    .param("multiple", "false")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))

        then: "should return 400 Bad Request with validation error"
            result.andExpect(status().isBadRequest())
                    .andExpect(jsonPath('$.status').value(400))

            if (expectedErrors.size() == 1) {
                result.andExpect(jsonPath('$.errors[0]').value(expectedErrors[0]))
            } else {
                result.andExpect(jsonPath('$.errors.length()').value(expectedErrors.size()))
                        .andExpect(jsonPath('$.errors', containsInAnyOrder(expectedErrors as Object[])))
            }

            0 * productsService.scrapeProducts(_, _)

        where:
            scenario                          | url                  | category      || expectedErrors
            "URL is null"                     | null                 | "electronics" || ["url: URL is required"]
            "URL is blank"                    | "  "                 | "electronics" || ["url: URL is required", "url: URL must be a valid HTTP or HTTPS URL"]
            "URL is invalid format"           | "not-a-url"          | "electronics" || ["url: URL must be a valid HTTP or HTTPS URL"]
            "category is null"                | "https://example.com"| null          || ["category: Category is required"]
            "category is blank"               | "https://example.com"| "  "          || ["category: Category is required"]
            "both URL and category are null"  | null                 | null          || ["category: Category is required", "url: URL is required"]
    }
}
