package org.skroutz.scraper.skroutzwebscraper.scraping.application.service.processing

import ch.qos.logback.classic.Level
import org.skroutz.scraper.skroutzwebscraper.base.WithLoggingBaseSpec
import org.skroutz.scraper.skroutzwebscraper.scraping.infrastructure.dto.api.ProductApiResponseDto
import org.skroutz.scraper.skroutzwebscraper.scraping.infrastructure.dto.ScraperRequestDto
import org.skroutz.scraper.skroutzwebscraper.scraping.infrastructure.scraper.ProductsScraper
import org.skroutz.scraper.skroutzwebscraper.product.application.service.ProductPersistenceService
import org.skroutz.scraper.skroutzwebscraper.scraping.application.service.processing.ProductScraperService
import org.skroutz.scraper.skroutzwebscraper.scraping.infrastructure.utils.UrlBuilder
import spock.lang.Subject

// Standard Spock spec dependency

class ProductScraperServiceSpec extends WithLoggingBaseSpec {

    ProductsScraper productsScraper = Mock(ProductsScraper)
    ProductPersistenceService productPersistenceService = Mock(ProductPersistenceService)
    UrlBuilder urlBuilder = new UrlBuilder("https://example.com")

    @Subject
    ProductScraperService service = new ProductScraperService(productsScraper, productPersistenceService, urlBuilder)

    def "Happy path, scrape products from single page and delegate to persistence service"() {
        given: "a scraper request for single page"
            def scraperRequestDto = new ScraperRequestDto(url: "https://example.com/products.html", category: "electronics")

        and: "a mock API response"
            ProductApiResponseDto apiResponseDto = new ProductApiResponseDto(items: [])

        when: "scraping products from single page"
            service.scrapeProducts(scraperRequestDto, false)

        then: "orchestration calls productsScraper and delegates the saving payload"
            1 * productsScraper.fetchProductsPage("https://example.com/products.json") >> apiResponseDto
            1 * productPersistenceService.saveOrUpdateProducts(apiResponseDto, "electronics")
            0 * _

        and: "log should contain info about scraped products"
            assertLog(Level.INFO, "Finished scraping page: https://example.com/products.html")
    }

    def "Happy path, scrape products from multiple pages and delegate each page to persistence"() {
        given: "a scraper request for multiple pages"
            def scraperRequestDto = new ScraperRequestDto(url: "https://example.com/products.html", category: "electronics")

        and: "mock API responses for page 1 and page 2"
            ProductApiResponseDto apiResponseDtoPage1 = new ProductApiResponseDto(
                    items: [],
                    page: new ProductApiResponseDto.PageDetailsResponseDto(totalPages: 2, currentPage: 1)
            )
            ProductApiResponseDto apiResponseDtoPage2 = new ProductApiResponseDto(
                    items: [],
                    page: new ProductApiResponseDto.PageDetailsResponseDto(totalPages: 2, currentPage: 2)
            )

        when: "scraping products from multiple pages"
            service.scrapeProducts(scraperRequestDto, true)

        then: "it fetches the page count, maps URLs, and processes the persistence sequentially"
            2 * productsScraper.fetchProductsPage("https://example.com/products.json") >> apiResponseDtoPage1
            1 * productsScraper.fetchProductsPage("https://example.com/products.json?page=2") >> apiResponseDtoPage2
            1 * productPersistenceService.saveOrUpdateProducts(apiResponseDtoPage1, "electronics")
            1 * productPersistenceService.saveOrUpdateProducts(apiResponseDtoPage2, "electronics")
            0 * _

        and: "pagination progress is tracked in the log logs"
            assertLog(Level.INFO, "Found 2 pages to scrape")
            assertLog(Level.INFO, "Finished scraping all pages.")
    }

    def "Unhappy path, should log warn and return early if total pages is less than or equal to 0"() {
        given: "a scraper request for multiple pages"
            def scraperRequestDto = new ScraperRequestDto(url: "https://example.com/products.html", category: "electronics")

        and: "a response indicating 0 pages"
            ProductApiResponseDto apiResponseDto = new ProductApiResponseDto(
                    items: [],
                    page: new ProductApiResponseDto.PageDetailsResponseDto(totalPages: 0, currentPage: 0)
            )

        when: "scraping logic evaluates pages"
            service.scrapeProducts(scraperRequestDto, true)

        then: "it checks pages and stops execution safely"
            1 * productsScraper.fetchProductsPage("https://example.com/products.json") >> apiResponseDto
            0 * productPersistenceService.saveOrUpdateProducts(_, _)
            0 * _

        and: "warning log is triggered"
            assertLog(Level.WARN, "No pages found for URL: https://example.com/products.html")
    }

    def "Unhappy path, should handle and log error gracefully if scraping execution throws exception"() {
        given: "a standard scraper request"
            def scraperRequestDto = new ScraperRequestDto(url: "https://example.com/products.html", category: "electronics")

        when: "scraping execution fails unexpectedly"
            service.scrapeProducts(scraperRequestDto, false)

        then: "the target resource throws an exception"
            1 * productsScraper.fetchProductsPage("https://example.com/products.json") >> { throw new RuntimeException("Network timeout") }
            0 * _

        and: "error log contains operational contextual metadata"
            assertLog(Level.ERROR, "Failed scraping page https://example.com/products.html: Network timeout")
    }
}