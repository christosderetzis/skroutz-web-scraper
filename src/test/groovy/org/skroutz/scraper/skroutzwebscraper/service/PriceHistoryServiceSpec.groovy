package org.skroutz.scraper.skroutzwebscraper.service

import ch.qos.logback.classic.Level
import org.skroutz.scraper.skroutzwebscraper.base.WithLoggingBaseSpec
import org.skroutz.scraper.skroutzwebscraper.entity.Product
import org.skroutz.scraper.skroutzwebscraper.repository.ProductRepository
import spock.lang.Subject

class PriceHistoryServiceSpec extends WithLoggingBaseSpec {

    ProductRepository productRepository = Mock()
    PriceHistoryTxService priceHistoryTxService = Mock()

    @Subject
    PriceHistoryService priceHistoryService

    def setup() {
        priceHistoryService = new PriceHistoryService(priceHistoryTxService, productRepository)
        priceHistoryService.delayMs = 0 // Disable delay for testing
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

        then: "products are fetched and processed"
            1 * productRepository.findAllByPriceHistoryParsed(false) >> [product1, product2]
            1 * priceHistoryTxService.processSingleProduct(product1, "https://www.skroutz.gr/s/123456/product-name/price_graph.json?shipping_country=GR&currency=EUR")
            1 * priceHistoryTxService.processSingleProduct(product2, "https://www.skroutz.gr/s/789012/another-product/price_graph.json?shipping_country=GR&currency=EUR")
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

    def "Happy path, process multiple products with mixed valid and invalid URLs"() {
        given: "mix of valid and invalid products"
            Product validProduct = Product.builder()
                    .id(1L)
                    .url("https://www.skroutz.gr/s/123456/product.html")
                    .title("Valid Product")
                    .priceHistoryParsed(false)
                    .build()

            Product nullUrlProduct = Product.builder()
                    .id(2L)
                    .url(null)
                    .title("Null URL Product")
                    .priceHistoryParsed(false)
                    .build()

            Product blankUrlProduct = Product.builder()
                    .id(3L)
                    .url("  ")
                    .title("Blank URL Product")
                    .priceHistoryParsed(false)
                    .build()

        when: "fetchPriceHistoryForProducts is called"
            priceHistoryService.fetchPriceHistoryForProducts()

        then: "only valid product is processed"
            1 * productRepository.findAllByPriceHistoryParsed(false) >> [validProduct, nullUrlProduct, blankUrlProduct]
            1 * priceHistoryTxService.processSingleProduct(validProduct, _)
            0 * _

        and: "warnings are logged for invalid products"
            assertLog(Level.WARN, "Product URL is empty or null for product ID: 2")
            assertLog(Level.WARN, "Product URL is empty or null for product ID: 3")
    }

    def "Happy path, URL formatting - #scenario"() {
        given: "product with URL"
            Product product = Product.builder()
                    .id(1L)
                    .url(inputUrl)
                    .title("Product")
                    .priceHistoryParsed(false)
                    .build()

        when: "fetchPriceHistoryForProducts is called"
            priceHistoryService.fetchPriceHistoryForProducts()

        then: "URL is formatted correctly"
            1 * productRepository.findAllByPriceHistoryParsed(false) >> [product]
            1 * priceHistoryTxService.processSingleProduct(product, expectedUrl)
            0 * _

        where: "different URL formats"
            scenario                              | inputUrl                                                                   | expectedUrl
            "without .html extension"             | "https://www.skroutz.gr/s/123456/product-name"                            | "https://www.skroutz.gr/s/123456/product-name/price_graph.json?shipping_country=GR&currency=EUR"
            "with .html and query parameters"     | "https://www.skroutz.gr/s/123456/product.html?ref=home&campaign=test"     | "https://www.skroutz.gr/s/123456/product/price_graph.json?shipping_country=GR&currency=EUR"
    }

    def "Unhappy path, interrupted exception during sleep"() {
        given: "product that will trigger interrupted exception"
            Product product = Product.builder()
                    .id(1L)
                    .url("https://www.skroutz.gr/s/123456/product.html")
                    .title("Product")
                    .priceHistoryParsed(false)
                    .build()

        and: "service with delay enabled"
            priceHistoryService.delayMs = 100

        and: "mock thread interruption"
            Thread.currentThread().interrupt()

        when: "fetchPriceHistoryForProducts is called"
            priceHistoryService.fetchPriceHistoryForProducts()

        then: "exception is caught and logged"
            1 * productRepository.findAllByPriceHistoryParsed(false) >> [product]
            1 * priceHistoryTxService.processSingleProduct(product, _)
            0 * _

        and: "error is logged"
            assertLog(Level.ERROR, "Error processing product ID 1")

        cleanup:
            Thread.interrupted() // Clear the interrupted status
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
            1 * priceHistoryTxService.processSingleProduct(product1, _)
            1 * priceHistoryTxService.processSingleProduct(product2, _) >> { throw new RuntimeException("Failed") }
            1 * priceHistoryTxService.processSingleProduct(product3, _)
            0 * _

        and: "error is logged for failed product"
            assertLog(Level.ERROR, "Error processing product ID 2")
    }
}
