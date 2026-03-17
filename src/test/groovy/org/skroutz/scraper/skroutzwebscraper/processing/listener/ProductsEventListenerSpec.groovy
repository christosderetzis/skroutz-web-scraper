package org.skroutz.scraper.skroutzwebscraper.processing.listener

import org.skroutz.scraper.skroutzwebscraper.processing.entity.Product
import org.skroutz.scraper.skroutzwebscraper.processing.repository.ProductRepository
import org.skroutz.scraper.skroutzwebscraper.scraping.dto.ScrapedProductData
import org.skroutz.scraper.skroutzwebscraper.scraping.event.ProductsScrapedEvent
import spock.lang.Specification
import spock.lang.Subject

class ProductsEventListenerSpec extends Specification {

    ProductRepository productRepository = Mock()

    @Subject
    ProductsEventListener listener = new ProductsEventListener(productRepository)

    def "Saves new products when they don't exist"() {
        given: "scraped product data for new products"
            def product1 = new ScrapedProductData(
                    "Product 1",
                    "http://example.com/product1",
                    new BigDecimal("99.99"),
                    "Description 1",
                    new BigDecimal("4.5"),
                    "http://example.com/image1.jpg"
            )
            def product2 = new ScrapedProductData(
                    "Product 2",
                    "http://example.com/product2",
                    new BigDecimal("149.99"),
                    "Description 2",
                    new BigDecimal("3.8"),
                    "http://example.com/image2.jpg"
            )
            def event = new ProductsScrapedEvent([product1, product2])

        when: "the event is handled"
            listener.handleProductsScraped(event)

        then: "repository checks if each product exists by URL"
            1 * productRepository.findByUrl("http://example.com/product1") >> Optional.empty()
            1 * productRepository.findByUrl("http://example.com/product2") >> Optional.empty()

        and: "both products are saved"
            2 * productRepository.save({ Product p ->
                p.url in ["http://example.com/product1", "http://example.com/product2"]
            }) >> { Product p -> p }
    }

    def "Updates existing product when URL already exists with changed fields"() {
        given: "scraped product data with updated fields"
            def scrapedData = new ScrapedProductData(
                    "Product 1",
                    "http://example.com/product1",
                    new BigDecimal("79.99"),
                    "Updated description",
                    new BigDecimal("4.8"),
                    "http://example.com/new-image.jpg"
            )
            def event = new ProductsScrapedEvent([scrapedData])

        and: "an existing product in the database"
            def existingProduct = Product.builder()
                    .id(1L)
                    .title("Product 1")
                    .url("http://example.com/product1")
                    .price(new BigDecimal("99.99"))
                    .description("Old description")
                    .rating(new BigDecimal("4.5"))
                    .imageUrl("http://example.com/old-image.jpg")
                    .build()

        when: "the event is handled"
            listener.handleProductsScraped(event)

        then: "repository finds the existing product"
            1 * productRepository.findByUrl("http://example.com/product1") >> Optional.of(existingProduct)

        and: "the existing product is saved with updated fields"
            1 * productRepository.save({ Product p ->
                p.id == 1L &&
                p.price == new BigDecimal("79.99") &&
                p.description == "Updated description" &&
                p.rating == new BigDecimal("4.8") &&
                p.imageUrl == "http://example.com/new-image.jpg"
            }) >> { Product p -> p }
    }

    def "Skips product with null URL or title"() {
        given: "scraped product data with null URL and null title"
            def nullUrlProduct = new ScrapedProductData(
                    "Product 1",
                    null,
                    new BigDecimal("99.99"),
                    "Description",
                    new BigDecimal("4.5"),
                    "http://example.com/image.jpg"
            )
            def nullTitleProduct = new ScrapedProductData(
                    null,
                    "http://example.com/product2",
                    new BigDecimal("49.99"),
                    "Description",
                    new BigDecimal("3.0"),
                    "http://example.com/image2.jpg"
            )
            def event = new ProductsScrapedEvent([nullUrlProduct, nullTitleProduct])

        when: "the event is handled"
            listener.handleProductsScraped(event)

        then: "no products are looked up or saved"
            0 * productRepository.findByUrl(_)
            0 * productRepository.save(_)
    }

    def "Handles save error gracefully and continues with other products"() {
        given: "two scraped products where the first causes a save error"
            def product1 = new ScrapedProductData(
                    "Product 1",
                    "http://example.com/product1",
                    new BigDecimal("99.99"),
                    "Description 1",
                    new BigDecimal("4.5"),
                    "http://example.com/image1.jpg"
            )
            def product2 = new ScrapedProductData(
                    "Product 2",
                    "http://example.com/product2",
                    new BigDecimal("149.99"),
                    "Description 2",
                    new BigDecimal("3.8"),
                    "http://example.com/image2.jpg"
            )
            def event = new ProductsScrapedEvent([product1, product2])

        when: "the event is handled"
            listener.handleProductsScraped(event)

        then: "first product lookup succeeds but save throws an exception"
            1 * productRepository.findByUrl("http://example.com/product1") >> Optional.empty()
            1 * productRepository.save({ Product p -> p.url == "http://example.com/product1" }) >> {
                throw new RuntimeException("Database error")
            }

        and: "second product is still processed successfully"
            1 * productRepository.findByUrl("http://example.com/product2") >> Optional.empty()
            1 * productRepository.save({ Product p -> p.url == "http://example.com/product2" }) >> { Product p -> p }

        and: "no exception is thrown"
            noExceptionThrown()
    }
}
