package org.skroutz.scraper.skroutzwebscraper.scraping

import com.fasterxml.jackson.databind.JsonNode
import org.skroutz.scraper.skroutzwebscraper.scraping.dto.PriceHistoryResponseDto
import org.skroutz.scraper.skroutzwebscraper.scraping.event.PriceHistoryScrapedEvent
import org.skroutz.scraper.skroutzwebscraper.scraping.event.SpecificationsScrapedEvent
import org.skroutz.scraper.skroutzwebscraper.scraping.scraper.PriceHistoryScraper
import org.skroutz.scraper.skroutzwebscraper.scraping.scraper.ProductsScraper
import org.skroutz.scraper.skroutzwebscraper.scraping.scraper.ReviewsScraper
import org.skroutz.scraper.skroutzwebscraper.scraping.scraper.SpecificationsScraper
import org.springframework.context.ApplicationEventPublisher
import spock.lang.Specification
import spock.lang.Subject

class ScrapingServiceSpec extends Specification {

    ProductsScraper productsScraper = Mock()
    ReviewsScraper reviewsScraper = Mock()
    PriceHistoryScraper priceHistoryScraper = Mock()
    SpecificationsScraper specificationsScraper = Mock()
    ApplicationEventPublisher eventPublisher = Mock()

    @Subject
    ScrapingService service = new ScrapingService(productsScraper, reviewsScraper, priceHistoryScraper, specificationsScraper, eventPublisher)

    def "scrapePriceHistory strips .html from URL before fetching price graph"() {
        given:
            String productUrl = "https://www.skroutz.gr/s/12345/product.html"
            PriceHistoryResponseDto response = PriceHistoryResponseDto.builder().build()

        when:
            service.scrapePriceHistory(1L, productUrl)

        then:
            1 * priceHistoryScraper.fetchPriceHistory("https://www.skroutz.gr/s/12345/product/price_graph.json?shipping_country=GR&currency=EUR") >> response
            1 * eventPublisher.publishEvent(_ as PriceHistoryScrapedEvent)
            0 * _
    }

    def "scrapePriceHistory builds price graph URL when URL has no .html extension"() {
        given:
            String productUrl = "https://www.skroutz.gr/s/12345/product"
            PriceHistoryResponseDto response = PriceHistoryResponseDto.builder().build()

        when:
            service.scrapePriceHistory(1L, productUrl)

        then:
            1 * priceHistoryScraper.fetchPriceHistory("https://www.skroutz.gr/s/12345/product/price_graph.json?shipping_country=GR&currency=EUR") >> response
            1 * eventPublisher.publishEvent(_ as PriceHistoryScrapedEvent)
            0 * _
    }

    def "scrapeSpecifications appends ?lang=en when URL has no query parameters"() {
        given:
            String productUrl = "https://www.skroutz.gr/s/12345/product.html"
            JsonNode specs = Mock()

        when:
            service.scrapeSpecifications(1L, productUrl)

        then:
            1 * specificationsScraper.scrapeSpecifications("https://www.skroutz.gr/s/12345/product.html?lang=en") >> specs
            1 * eventPublisher.publishEvent(_ as SpecificationsScrapedEvent)
            0 * _
    }

    def "scrapeSpecifications appends &lang=en when URL already has query parameters"() {
        given:
            String productUrl = "https://www.skroutz.gr/s/12345/product.html?filter=1"
            JsonNode specs = Mock()

        when:
            service.scrapeSpecifications(1L, productUrl)

        then:
            1 * specificationsScraper.scrapeSpecifications("https://www.skroutz.gr/s/12345/product.html?filter=1&lang=en") >> specs
            1 * eventPublisher.publishEvent(_ as SpecificationsScrapedEvent)
            0 * _
    }
}
