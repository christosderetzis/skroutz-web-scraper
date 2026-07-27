package org.skroutz.scraper.skroutzwebscraper.specs

import org.skroutz.scraper.skroutzwebscraper.scraping.infrastructure.dto.ScraperRequestDto
import org.skroutz.scraper.skroutzwebscraper.utils.base.BaseFunctionalSpec


class ScrapeProductsFunctionalSpec extends BaseFunctionalSpec {

    def "Scrape products with single, happy path"() {
        given:
            ScraperRequestDto requestDto = ScraperRequestDto.builder()
                    .url("http://localhost:8081/products-page.html")
                    .category("electronics")
                    .build()

        when:
            def response = webActor.scrapeProducts(requestDto, false)
            webActor.waitForJobCompletion(response)

        then:
            def products = productRepository.findAll()
            assert products.size() == 4

        and: "all product URLs should start with base URL"
            assert products.every { it.url.startsWith("http://localhost:8081") }

        and: "all products should have the category set"
            assert products.every { it.category == "electronics" }
    }

    def "Scrape products, happy path with all pages"() {
        given:
            ScraperRequestDto requestDto = ScraperRequestDto.builder()
                    .url("http://localhost:8081//products-page.html")
                    .category("smartphones")
                    .build()

        when:
            def response = webActor.scrapeProducts(requestDto, true)
            webActor.waitForJobCompletion(response)

        then:
            def products = productRepository.findAll()
            assert products.size() == 15

        and: "all product URLs should start with base URL"
            assert products.every { it.url.startsWith("http://localhost:8081") }

        and: "all products should have the category set"
            assert products.every { it.category == "smartphones" }
    }
}
