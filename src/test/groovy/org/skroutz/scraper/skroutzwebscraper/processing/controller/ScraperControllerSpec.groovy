package org.skroutz.scraper.skroutzwebscraper.processing.controller

import com.fasterxml.jackson.databind.ObjectMapper
import org.skroutz.scraper.skroutzwebscraper.processing.dto.ScraperRequestDto
import org.skroutz.scraper.skroutzwebscraper.processing.service.PriceHistoryService
import org.skroutz.scraper.skroutzwebscraper.processing.service.ProductsService
import org.skroutz.scraper.skroutzwebscraper.processing.service.ReviewsService
import org.skroutz.scraper.skroutzwebscraper.processing.service.SpecificationsService
import org.spockframework.spring.SpringBean
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.web.servlet.MockMvc
import spock.lang.Specification

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest
@ContextConfiguration(classes = [ScraperController])
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
            def scraperRequestDto = new ScraperRequestDto(url: "https://example.com/products")
            def requestBody = objectMapper.writeValueAsString(scraperRequestDto)

        when: "performing POST request with multiple=false"
            def result = mockMvc.perform(post("/scraper/products")
                    .param("multiple", "false")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))

        then: "should scrape single page and return OK"
            result.andExpect(status().isOk())
            1 * productsService.scrapeAndSaveProducts("https://example.com/products")
            0 * productsService.getNumberOfWebPages(_)
    }

    def "should scrape multiple pages when multiple is true and pages exist"() {
        given: "a scraper request for multiple pages"
            def scraperRequestDto = new ScraperRequestDto(url: "https://example.com/products")

        when: "performing POST request with multiple=true"
            def result = mockMvc.perform(post("/scraper/products")
                    .param("multiple", "true")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(scraperRequestDto)))

        then: "should scrape all pages and return OK"
            result.andExpect(status().isOk())
            1 * productsService.getNumberOfWebPages("https://example.com/products") >> 3
            1 * productsService.scrapeAndSaveProducts("https://example.com/products")
            1 * productsService.scrapeAndSaveProducts("https://example.com/products?page=2")
            1 * productsService.scrapeAndSaveProducts("https://example.com/products?page=3")
    }

    def "should handle URL with existing query parameters when scraping multiple pages"() {
        given: "a scraper request with URL containing query parameters"
            def scraperRequestDto = new ScraperRequestDto(url: "https://example.com/products?category=electronics")
            def requestBody = objectMapper.writeValueAsString(scraperRequestDto)

        when: "performing POST request with multiple=true"
            def result = mockMvc.perform(post("/scraper/products")
                    .param("multiple", "true")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))

        then: "should append page parameter with ampersand and return OK"
            result.andExpect(status().isOk())
            1 * productsService.getNumberOfWebPages("https://example.com/products?category=electronics") >> 3
            1 * productsService.scrapeAndSaveProducts("https://example.com/products?category=electronics")
            1 * productsService.scrapeAndSaveProducts("https://example.com/products?category=electronics&page=2")
            1 * productsService.scrapeAndSaveProducts("https://example.com/products?category=electronics&page=3")
    }

    def "should return OK when no pages found for multiple scraping"() {
        given: "a scraper request for multiple pages"
            def scraperRequestDto = new ScraperRequestDto(url: "https://example.com/products")
            def requestBody = objectMapper.writeValueAsString(scraperRequestDto)

        when: "performing POST request with multiple=true"
            def result = mockMvc.perform(post("/scraper/products")
                    .param("multiple", "true")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))

        then: "should return OK without scraping any pages"
            result.andExpect(status().isOk())
            1 * productsService.getNumberOfWebPages("https://example.com/products") >> 0
            0 * productsService.scrapeAndSaveProducts(_)
    }

    def "should return OK when negative pages found for multiple scraping"() {
        given: "a scraper request for multiple pages"
            def scraperRequestDto = new ScraperRequestDto(url: "https://example.com/products")
            def requestBody = objectMapper.writeValueAsString(scraperRequestDto)

        when: "performing POST request with multiple=true"
            def result = mockMvc.perform(post("/scraper/products")
                    .param("multiple", "true")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))

        then: "should return OK without scraping any pages"
            result.andExpect(status().isOk())
            1 * productsService.getNumberOfWebPages("https://example.com/products") >> -1
            0 * productsService.scrapeAndSaveProducts(_)
    }

    def "should handle single page scraping correctly in multiple mode"() {
        given: "a scraper request for multiple pages with only one page"
            def scraperRequestDto = new ScraperRequestDto(url: "https://example.com/products")
            def requestBody = objectMapper.writeValueAsString(scraperRequestDto)

        when: "performing POST request with multiple=true"
            def result = mockMvc.perform(post("/scraper/products")
                    .param("multiple", "true")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))

        then: "should scrape only the base URL without page parameter"
            result.andExpect(status().isOk())
            1 * productsService.getNumberOfWebPages("https://example.com/products") >> 1
            1 * productsService.scrapeAndSaveProducts("https://example.com/products")
    }

    def "should handle complex URL with multiple query parameters"() {
        given: "a scraper request with complex URL"
            def scraperRequestDto = new ScraperRequestDto(url: "https://example.com/products?category=electronics&sort=price&filter=available")
            def requestBody = objectMapper.writeValueAsString(scraperRequestDto)

        when: "performing POST request with multiple=true"
            def result = mockMvc.perform(post("/scraper/products")
                    .param("multiple", "true")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody))

        then: "should handle complex URL correctly"
            result.andExpect(status().isOk())
            1 * productsService.getNumberOfWebPages("https://example.com/products?category=electronics&sort=price&filter=available") >> 2
            1 * productsService.scrapeAndSaveProducts("https://example.com/products?category=electronics&sort=price&filter=available")
            1 * productsService.scrapeAndSaveProducts("https://example.com/products?category=electronics&sort=price&filter=available&page=2")
    }

    def "should scrape price history successfully"() {
        when: "performing POST request to scrape price history"
            def result = mockMvc.perform(post("/scraper/price-history"))

        then: "should call price history service and return OK"
            result.andExpect(status().isOk())
            1 * priceHistoryService.fetchPriceHistoryForProducts()
    }
}
