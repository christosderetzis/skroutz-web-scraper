package org.skroutz.scraper.skroutzwebscraper.service

import ch.qos.logback.classic.Level
import org.skroutz.scraper.skroutzwebscraper.base.WithLoggingBaseSpec
import org.skroutz.scraper.skroutzwebscraper.dto.ScraperRequestDto
import org.skroutz.scraper.skroutzwebscraper.entity.Product
import org.skroutz.scraper.skroutzwebscraper.mapper.ProductMapper
import org.skroutz.scraper.skroutzwebscraper.repository.ProductRepository
import org.skroutz.scraper.skroutzwebscraper.scraper.ProductsScraper
import spock.lang.Subject
import spock.lang.Unroll

class ProductsServiceSpec extends WithLoggingBaseSpec {

    ProductsScraper productsScraper = Mock(ProductsScraper)
    ProductMapper productMapper = Mock(ProductMapper)
    ProductRepository productRepository = Mock(ProductRepository)

    @Subject
    ProductsService service = new ProductsService(productsScraper, productRepository, productMapper)

    def "Happy path, should scrape and save products if products do not exist"() {
        given: "a scraper request"
            def scraperRequestDto = new ScraperRequestDto(
                url: "http://example.com/products",
                category: "electronics"
            )

        and: "a list of scraped products"
            List<Product> scrapedProducts = [
                    new Product(title: "Product 1", price: 100, url: "http://example.com/product1", category: "electronics"),
                    new Product(title: "Product 2", price: 200, url: "http://example.com/product2", category: "electronics")
            ]

        when: "scraping and saving products"
            service.scrapeAndSaveProducts(scraperRequestDto)

        then: "products should be scraped from the URL"
            1 * productsScraper.scrapeProducts({ it.url == "http://example.com/products" && it.category == "electronics" }) >> scrapedProducts
            scrapedProducts.each {
                1 * productRepository.findByUrl(it.url) >> Optional.empty()
                1 * productRepository.save(it) >> it
            }
            0 * _

        and: "log should contain info about saved products"
            assertLog(Level.INFO, "Successfully saved 2 new products to database")
    }

    def "Happy path, Should products if they already exist"() {
        given: "a scraper request"
            def scraperRequestDto = new ScraperRequestDto(
                url: "http://example.com/products",
                category: "electronics"
            )

        and: "an existing product and another that was scraped with different price"
            Product existingProduct = new Product(title: "Product 1", price: 100, url: "http://example.com/product1", category: "electronics")
            Product scrapedProductWithNewPrice = new Product(title: "Product 1", price: 250, url: "http://example.com/product1", category: "electronics")

        when: "scraping and saving products"
            service.scrapeAndSaveProducts(scraperRequestDto)

        then: "products should be scraped from the URL"
            1 * productsScraper.scrapeProducts({ it.url == "http://example.com/products" && it.category == "electronics" }) >> [scrapedProductWithNewPrice]
            1 * productRepository.findByUrl(scrapedProductWithNewPrice.url) >> Optional.of(existingProduct)
            1 * productRepository.save(scrapedProductWithNewPrice)
            0 * _
    }

    def "Happy path, should not save products if they already exist with same price"() {
        given: "a scraper request"
            def scraperRequestDto = new ScraperRequestDto(
                url: "http://example.com/products",
                category: "electronics"
            )

        and: "an existing product and another that was scraped with same price"
            Product existingProduct = new Product(title: "Product 1", price: 100, url: "http://example.com/product1", category: "electronics")

        when: "scraping and saving products"
            service.scrapeAndSaveProducts(scraperRequestDto)

        then: "products should be scraped from the URL"
            1 * productsScraper.scrapeProducts({ it.url == "http://example.com/products" && it.category == "electronics" }) >> [existingProduct]
            1 * productRepository.findByUrl(existingProduct.url) >> Optional.of(existingProduct)
            0 * productRepository.save(_)
            0 * _
    }

    def "Unhappy path, should log error if scraping fails"() {
        given: "a scraper request"
            def scraperRequestDto = new ScraperRequestDto(
                url: "http://example.com/products",
                category: "electronics"
            )

        when: "scraping and saving products"
            service.scrapeAndSaveProducts(scraperRequestDto)

        then: "scraper throws exception"
            1 * productsScraper.scrapeProducts({ it.url == "http://example.com/products" && it.category == "electronics" }) >> { throw new Exception("Scraping failed") }
            0 * _

        and:
            def ex = thrown(RuntimeException)
            ex.message.contains("Failed to scrape and save products")
    }

    @Unroll
    def "Unhappy path, should do nothing if scraped product #field is null"() {
        given: "a scraper request"
            def scraperRequestDto = new ScraperRequestDto(
                url: "http://example.com/products",
                category: "electronics"
            )

        and: "a list of scraped products with one having null URL"
            Product product = new Product(title: "Product 1", price: 100, url: "http://example.com/product1", category: "electronics")
            product."$field" = value

        when: "scraping and saving products"
            service.scrapeAndSaveProducts(scraperRequestDto)

        then: "products should be scraped from the URL and not saved"
            1 * productsScraper.scrapeProducts({ it.url == "http://example.com/products" && it.category == "electronics" }) >> [product]
            0 * _

        where:
           field   | value
           "url"   | null
           "title" | null
    }

    def "Happy path, should return number of web pages"() {
        given: "a URL to check"
            String url = "http://example.com/products"

        when: "getting number of web pages"
            int pages = service.getNumberOfWebPages(url)

        then: "should return the number of pages from the scraper"
            1 * productsScraper.getNumberOfPages(url) >> 5
            0 * _

        and: "should return correct number of pages"
            pages == 5
    }

    def "Unhappy path, should log error if getting number of pages fails"() {
        given: "a URL to check"
            String url = "http://example.com/products"

        when: "getting number of web pages"
            service.getNumberOfWebPages(url)

        then: "scraper throws exception"
            1 * productsScraper.getNumberOfPages(url) >> { throw new Exception("Failed to get pages") }
            0 * _

        and:
            def ex = thrown(RuntimeException)
            ex.message.contains("Failed to get number of web pages")
    }
}

