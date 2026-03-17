package org.skroutz.scraper.skroutzwebscraper.processing.service

import ch.qos.logback.classic.Level
import org.skroutz.scraper.skroutzwebscraper.base.WithLoggingBaseSpec
import org.skroutz.scraper.skroutzwebscraper.processing.entity.Product
import org.skroutz.scraper.skroutzwebscraper.processing.repository.ProductRepository
import org.skroutz.scraper.skroutzwebscraper.scraping.ScrapingService
import spock.lang.Subject

class PriceHistoryServiceSpec extends WithLoggingBaseSpec {

    ProductRepository productRepository = Mock()
    ScrapingService scrapingService = Mock()

    @Subject
    PriceHistoryService priceHistoryService

    def setup() {
        priceHistoryService = new PriceHistoryService(scrapingService, productRepository)
        priceHistoryService.delayMs = 0
    }

    def "Happy path, fetch price history for products successfully"() {
        given: "products without price history parsed"
            Product product1 = Product.builder()
                    .id(1L)
                    .url("https://www.skroutz.gr/s/123456/product-name.html")
                    .title("Product 1")
                    .priceHistoryParsed(false)
                    .build()

            Product product2 = Product.builder()
                    .id(2L)
                    .url("https://www.skroutz.gr/s/789012/another-product.html?param=value")
                    .title("Product 2")
                    .priceHistoryParsed(false)
                    .build()

        when: "fetchPriceHistoryForProducts is called"
            priceHistoryService.fetchPriceHistoryForProducts()

        then: "products are fetched and scraping is triggered"
            1 * productRepository.findAllByPriceHistoryParsed(false) >> [product1, product2]
            1 * scrapingService.scrapePriceHistory(1L, "https://www.skroutz.gr/s/123456/product-name.html")
            1 * scrapingService.scrapePriceHistory(2L, "https://www.skroutz.gr/s/789012/another-product.html?param=value")
            0 * _
    }

    def "Happy path, no products to process"() {
        when: "fetchPriceHistoryForProducts is called with no products"
            priceHistoryService.fetchPriceHistoryForProducts()

        then: "no processing occurs"
            1 * productRepository.findAllByPriceHistoryParsed(false) >> []
            0 * _
    }

    def "Happy path, skip product with #scenario"() {
        given: "product with invalid URL"
            Product product = Product.builder()
                    .id(productId)
                    .url(url)
                    .title("Product with ${scenario}")
                    .priceHistoryParsed(false)
                    .build()

        when: "fetchPriceHistoryForProducts is called"
            priceHistoryService.fetchPriceHistoryForProducts()

        then: "product is skipped"
            1 * productRepository.findAllByPriceHistoryParsed(false) >> [product]
            0 * _

        and: "warning is logged"
            assertLog(Level.WARN, "Product URL is empty or null for product ID: ${productId}")

        where: "different invalid URL scenarios"
            scenario   | productId | url
            "null URL" | 1L        | null
            "blank URL"| 2L        | "   "
            "empty URL"| 3L        | ""
    }

    def "Happy path, process continues after one product fails"() {
        given: "multiple products where one will fail"
            Product product1 = Product.builder()
                    .id(1L)
                    .url("https://www.skroutz.gr/s/123/product1.html")
                    .title("Product 1")
                    .priceHistoryParsed(false)
                    .build()

            Product product2 = Product.builder()
                    .id(2L)
                    .url("https://www.skroutz.gr/s/456/product2.html")
                    .title("Product 2")
                    .priceHistoryParsed(false)
                    .build()

            Product product3 = Product.builder()
                    .id(3L)
                    .url("https://www.skroutz.gr/s/789/product3.html")
                    .title("Product 3")
                    .priceHistoryParsed(false)
                    .build()

        when: "fetchPriceHistoryForProducts is called"
            priceHistoryService.fetchPriceHistoryForProducts()

        then: "all products are attempted despite one failure"
            1 * productRepository.findAllByPriceHistoryParsed(false) >> [product1, product2, product3]
            1 * scrapingService.scrapePriceHistory(1L, _)
            1 * scrapingService.scrapePriceHistory(2L, _) >> { throw new RuntimeException("Failed") }
            1 * scrapingService.scrapePriceHistory(3L, _)
            0 * _

        and: "error is logged for failed product"
            assertLog(Level.ERROR, "Error processing product ID 2")
    }
}
