package org.skroutz.scraper.skroutzwebscraper.scraping.application.controller

import com.fasterxml.jackson.databind.ObjectMapper
import org.skroutz.scraper.skroutzwebscraper.common.exception.RestResponseEntityExceptionHandler
import org.skroutz.scraper.skroutzwebscraper.scraping.application.service.AsyncScrapingFacade
import org.skroutz.scraper.skroutzwebscraper.scraping.application.service.ScrapeJobService
import org.skroutz.scraper.skroutzwebscraper.scraping.domain.enums.ScrapeJobStatus
import org.skroutz.scraper.skroutzwebscraper.scraping.domain.enums.ScrapeJobType
import org.skroutz.scraper.skroutzwebscraper.scraping.infrastructure.dto.ScrapeJobResponseDto
import org.skroutz.scraper.skroutzwebscraper.scraping.infrastructure.dto.ScraperRequestDto
import org.skroutz.scraper.skroutzwebscraper.scraping.infrastructure.exception.JobAlreadyRunningException
import org.spockframework.spring.SpringBean
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.web.servlet.MockMvc
import spock.lang.Specification

import java.time.LocalDateTime
import java.util.UUID

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
    ScrapeJobService scrapeJobService = Mock(ScrapeJobService)

    @SpringBean
    AsyncScrapingFacade asyncScrapingFacade = Mock(AsyncScrapingFacade)

    ObjectMapper objectMapper = new ObjectMapper()

       def runningJob(ScrapeJobType type) {
         def jobId = new Random().nextLong()
         return new ScrapeJobResponseDto(jobId, type.name(), ScrapeJobStatus.RUNNING.name(), LocalDateTime.now(), null, null)
    }

    def "should start product scraping and return 202 Accepted"() {
        given:
            def dto = runningJob(ScrapeJobType.SCRAPE_PRODUCTS)
            def request = new ScraperRequestDto(url: "https://example.com/products", category: "electronics")
            scrapeJobService.startJob(ScrapeJobType.SCRAPE_PRODUCTS) >> dto

        when:
            def result = mockMvc.perform(post("/scraper/products")
                    .param("multiple", "false")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))

        then:
            result.andExpect(status().isAccepted())
                    .andExpect(jsonPath('$.id').value(dto.id.toString()))
                    .andExpect(jsonPath('$.status').value("RUNNING"))
            1 * asyncScrapingFacade.runProductScraping(dto.id, _, false)
    }

    def "should start reviews scraping and return 202 Accepted"() {
        given:
            def dto = runningJob(ScrapeJobType.SCRAPE_REVIEWS)
            scrapeJobService.startJob(ScrapeJobType.SCRAPE_REVIEWS) >> dto

        when:
            def result = mockMvc.perform(post("/scraper/reviews"))

        then:
            result.andExpect(status().isAccepted())
                    .andExpect(jsonPath('$.jobType').value("SCRAPE_REVIEWS"))
            1 * asyncScrapingFacade.runReviewsScraping(dto.id)
    }

    def "should start specifications scraping and return 202 Accepted"() {
        given:
            def dto = runningJob(ScrapeJobType.SCRAPE_SPECIFICATIONS)
            scrapeJobService.startJob(ScrapeJobType.SCRAPE_SPECIFICATIONS) >> dto

        when:
            def result = mockMvc.perform(post("/scraper/specifications"))

        then:
            result.andExpect(status().isAccepted())
                    .andExpect(jsonPath('$.jobType').value("SCRAPE_SPECIFICATIONS"))
            1 * asyncScrapingFacade.runSpecificationsScraping(dto.id)
    }

    def "should start price history scraping and return 202 Accepted"() {
        given:
            def dto = runningJob(ScrapeJobType.SCRAPE_PRICE_HISTORY)
            scrapeJobService.startJob(ScrapeJobType.SCRAPE_PRICE_HISTORY) >> dto

        when:
            def result = mockMvc.perform(post("/scraper/price-history"))

        then:
            result.andExpect(status().isAccepted())
                    .andExpect(jsonPath('$.jobType').value("SCRAPE_PRICE_HISTORY"))
            1 * asyncScrapingFacade.runPriceHistoryScraping(dto.id)
    }

    def "should return 409 when a job of the same type is already running"() {
        given:
            def existingJobId = new Random().nextLong()
            scrapeJobService.startJob(ScrapeJobType.SCRAPE_REVIEWS) >> {
                throw new JobAlreadyRunningException(ScrapeJobType.SCRAPE_REVIEWS, existingJobId)
            }

        when:
            def result = mockMvc.perform(post("/scraper/reviews"))

        then:
            result.andExpect(status().isConflict())
                    .andExpect(jsonPath('$.status').value(409))
            0 * asyncScrapingFacade._
    }

    def "should return 400 when request validation fails: #scenario"() {
        given:
            def scraperRequestDto = new ScraperRequestDto(url: url, category: category)

        when:
            def result = mockMvc.perform(post("/scraper/products")
                    .param("multiple", "false")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(scraperRequestDto)))

        then:
            result.andExpect(status().isBadRequest())
                    .andExpect(jsonPath('$.status').value(400))

            if (expectedErrors.size() == 1) {
                result.andExpect(jsonPath('$.errors[0]').value(expectedErrors[0]))
            } else {
                result.andExpect(jsonPath('$.errors.length()').value(expectedErrors.size()))
                        .andExpect(jsonPath('$.errors', containsInAnyOrder(expectedErrors as Object[])))
            }

            0 * scrapeJobService._

        where:
            scenario                         | url                   | category      || expectedErrors
            "URL is null"                    | null                  | "electronics" || ["url: URL is required"]
            "URL is blank"                   | "  "                  | "electronics" || ["url: URL is required", "url: URL must be a valid HTTP or HTTPS URL"]
            "URL is invalid format"          | "not-a-url"           | "electronics" || ["url: URL must be a valid HTTP or HTTPS URL"]
            "category is null"               | "https://example.com" | null          || ["category: Category is required"]
            "category is blank"              | "https://example.com" | "  "          || ["category: Category is required"]
            "both URL and category are null" | null                  | null          || ["category: Category is required", "url: URL is required"]
    }
}
