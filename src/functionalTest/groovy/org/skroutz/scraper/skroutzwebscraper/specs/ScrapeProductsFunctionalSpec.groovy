package org.skroutz.scraper.skroutzwebscraper.specs

import org.skroutz.scraper.skroutzwebscraper.dto.ScraperRequestDto
import org.skroutz.scraper.skroutzwebscraper.utils.base.BaseFunctionalSpec
import org.springframework.test.web.reactive.server.WebTestClient

class ScrapeProductsFunctionalSpec extends BaseFunctionalSpec {

    def "Scrape products, happy path"() {
        given:
            Integer numberOfPages = 2
            ScraperRequestDto requestDto = ScraperRequestDto.builder()
                    .url("http://mockserver/products-page.html?numberOfPages=${numberOfPages}")
                    .build()

        when:
            WebTestClient.ResponseSpec response = webActor.scrapeProducts(requestDto, false)

        then:
            response.expectStatus().isOk()
            def products = productRepository.findAll()
            assert products.size() == 4

        and: "all product URLs should start with base URL"
            assert products.every { it.url.startsWith("http://mockserver") }
    }

    def "Scrape products, happy path with all pages"() {
        given:
            Integer numberOfPages = 2
            ScraperRequestDto requestDto = ScraperRequestDto.builder()
                    .url("http://mockserver/products-page.html?numberOfPages=${numberOfPages}")
                    .build()

        when:
            WebTestClient.ResponseSpec response = webActor.scrapeProducts(requestDto, true)

        then:
            response.expectStatus().isOk()
            def products = productRepository.findAll()
            assert products.size() == 15

        and: "all product URLs should start with base URL"
            assert products.every { it.url.startsWith("http://mockserver") }
    }
}
