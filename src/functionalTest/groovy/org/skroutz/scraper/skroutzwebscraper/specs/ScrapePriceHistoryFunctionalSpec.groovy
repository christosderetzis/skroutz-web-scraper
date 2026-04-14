package org.skroutz.scraper.skroutzwebscraper.specs

import org.skroutz.scraper.skroutzwebscraper.entity.Product
import org.skroutz.scraper.skroutzwebscraper.utils.base.BaseFunctionalSpec
import org.springframework.test.web.reactive.server.WebTestClient

import java.sql.Timestamp

class ScrapePriceHistoryFunctionalSpec extends BaseFunctionalSpec {

    def "Scrape price history with full data, happy path"() {
        given: "A product with a URL pointing to full price history data"
            Product product = Product.builder()
                    .title("Gaming Laptop")
                    .price(329.99)
                    .imageUrl("http://example.com/image.jpg")
                    .url("http://localhost:8081/product-full.html")
                    .description("High performance gaming laptop")
                    .rating(4.5)
                    .reviewsParsed(false)
                    .priceHistoryParsed(false)
                    .build()
            productRepository.save(product)

        when: "The price history scraping endpoint is called"
            WebTestClient.ResponseSpec response = webActor.scrapePriceHistory()

        then: "The response is successful"
            response.expectStatus().isOk()

        and: "Exactly 5 price history records are saved"
            def priceHistories = priceHistoryRepository.findAll().sort { it.priceDate }
            assert priceHistories.size() == 5

        and: "All records belong to the product"
            assert priceHistories.every { it.productId == product.getId() }

        and: "First record has exact data"
            with(priceHistories[0]) {
                price == 349.99
                storeName == "TechShop"
                priceDate == toTimestamp(1672531200)
            }

        and: "Second record has exact data"
            with(priceHistories[1]) {
                price == 339.99
                storeName == "ElectronicsHub"
                priceDate == toTimestamp(1680307200)
            }

        and: "Third record has exact data"
            with(priceHistories[2]) {
                price == 329.99
                storeName == "TechShop"
                priceDate == toTimestamp(1688169600)
            }

        and: "Fourth record has exact data"
            with(priceHistories[3]) {
                price == 319.99
                storeName == "ElectronicsHub"
                priceDate == toTimestamp(1696118400)
            }

        and: "Fifth record has exact data"
            with(priceHistories[4]) {
                price == 299.99
                storeName == "TechShop"
                priceDate == toTimestamp(1704067200)
            }

        and: "The product is marked as parsed"
            def updatedProduct = productRepository.findById(product.getId()).get()
            assert updatedProduct.priceHistoryParsed == true
    }

    def "Scrape price history with minimal data"() {
        given: "A product with a URL pointing to minimal price history data"
            Product product = Product.builder()
                    .title("Budget Phone")
                    .price(199.99)
                    .imageUrl("http://example.com/image.jpg")
                    .url("http://localhost:8081/product-minimal.html")
                    .description("Affordable smartphone")
                    .rating(3.5)
                    .reviewsParsed(false)
                    .priceHistoryParsed(false)
                    .build()
            productRepository.save(product)

        when: "The price history scraping endpoint is called"
            WebTestClient.ResponseSpec response = webActor.scrapePriceHistory()

        then: "The response is successful"
            response.expectStatus().isOk()

        and: "Exactly 1 price history record is saved"
            def priceHistories = priceHistoryRepository.findAll()
            assert priceHistories.size() == 1

        and: "Record has exact data"
            with(priceHistories[0]) {
                productId == product.getId()
                price == 199.99
                storeName == "SingleShop"
                priceDate == toTimestamp(1704067200)
            }

        and: "The product is marked as parsed"
            def updatedProduct = productRepository.findById(product.getId()).get()
            assert updatedProduct.priceHistoryParsed == true
    }

    def "Scrape price history with partial data - some time periods missing"() {
        given: "A product with a URL pointing to partial price history data"
            Product product = Product.builder()
                    .title("Wireless Mouse")
                    .price(149.99)
                    .imageUrl("http://example.com/image.jpg")
                    .url("http://localhost:8081/product-partial.html")
                    .description("Ergonomic wireless mouse")
                    .rating(4.0)
                    .reviewsParsed(false)
                    .priceHistoryParsed(false)
                    .build()
            productRepository.save(product)

        when: "The price history scraping endpoint is called"
            WebTestClient.ResponseSpec response = webActor.scrapePriceHistory()

        then: "The response is successful"
            response.expectStatus().isOk()

        and: "Exactly 2 price history records are saved"
            def priceHistories = priceHistoryRepository.findAll().sort { it.priceDate }
            assert priceHistories.size() == 2

        and: "All records belong to the product"
            assert priceHistories.every { it.productId == product.getId() }

        and: "First record has exact data"
            with(priceHistories[0]) {
                price == 179.99
                storeName == "BudgetStore"
                priceDate == toTimestamp(1688169600)
            }

        and: "Second record has exact data"
            with(priceHistories[1]) {
                price == 149.99
                storeName == "BudgetStore"
                priceDate == toTimestamp(1704067200)
            }

        and: "The product is marked as parsed"
            def updatedProduct = productRepository.findById(product.getId()).get()
            assert updatedProduct.priceHistoryParsed == true
    }

    def "Scrape price history handles API errors gracefully"() {
        given: "A product with a URL that returns an error"
            Product product = Product.builder()
                    .title("Error Product")
                    .price(99.99)
                    .imageUrl("http://example.com/image.jpg")
                    .url("http://localhost:8081/product-error.html")
                    .description("Product that will trigger an error")
                    .rating(3.0)
                    .reviewsParsed(false)
                    .priceHistoryParsed(false)
                    .build()
            productRepository.save(product)

        when: "The price history scraping endpoint is called"
            WebTestClient.ResponseSpec response = webActor.scrapePriceHistory()

        then: "The response is still successful (service continues despite individual errors)"
            response.expectStatus().isOk()

        and: "No price history data is saved for this product"
            def priceHistories = priceHistoryRepository.findAll()
            assert priceHistories.size() == 0

        and: "The product is not marked as parsed due to the error"
            def updatedProduct = productRepository.findById(product.getId()).get()
            assert updatedProduct.priceHistoryParsed == false
    }

    private static Timestamp toTimestamp(long epochSeconds) {
        return new Timestamp(epochSeconds * 1000)
    }
}
