package org.skroutz.scraper.skroutzwebscraper.service

import ch.qos.logback.classic.Level
import org.skroutz.scraper.skroutzwebscraper.base.WithLoggingBaseSpec
import org.skroutz.scraper.skroutzwebscraper.dto.ProductApiResponseDto
import org.skroutz.scraper.skroutzwebscraper.dto.ScraperRequestDto
import org.skroutz.scraper.skroutzwebscraper.entity.Product
import org.skroutz.scraper.skroutzwebscraper.mapper.ProductMapper
import org.skroutz.scraper.skroutzwebscraper.repository.ProductRepository
import org.skroutz.scraper.skroutzwebscraper.scraper.ProductsScraper
import org.skroutz.scraper.skroutzwebscraper.utils.UrlBuilder
import spock.lang.Subject

class ProductsServiceSpec extends WithLoggingBaseSpec {

    ProductsScraper productsScraper = Mock(ProductsScraper)
    ProductMapper productMapper = Mock(ProductMapper)
    ProductRepository productRepository = Mock(ProductRepository)
    UrlBuilder urlBuilder = new UrlBuilder("https://example.com")

    @Subject
    ProductsService service = new ProductsService(productsScraper, productRepository, productMapper, urlBuilder)

    def "Happy path, scrape products from single page and save if it does not exist"() {
        given: "a scraper request for single page"
            def scraperRequestDto = new ScraperRequestDto(url: "https://example.com/products.html", category: "electronics")

        and: "a list of scraped products"
            List<Product> scrapedProducts = [
                    new Product(title: "Product 1", price: 100, url: "http://example.com/product1", category: "electronics"),
            ]

        ProductApiResponseDto apiResponseDto = new ProductApiResponseDto(
                items: [
                    new ProductApiResponseDto.ProductDetailsResponseDto(title: "Product 1", price: 100, url: "http://example.com/product1"),
                ]
        )

        when: "scraping products from single page"
            service.scrapeProducts(scraperRequestDto, false)

        then: "products should be scraped from the URL"
            1 * productsScraper.fetchProductsPage("https://example.com/products.json") >> apiResponseDto
            1 * productMapper.toProduct(apiResponseDto.items[0], "electronics", urlBuilder) >> scrapedProducts[0]
            1 * productRepository.findByUrl(scrapedProducts[0].url) >> Optional.empty()
            1 * productRepository.save(scrapedProducts[0]) >> scrapedProducts[0]
            0 * _

        and: "log should contain info about scraped products"
            assertLog(Level.INFO, "Finished scraping page: https://example.com/products")
    }

    def "Happy path, scrape products from single page and do not save if it already exists with same price"() {
        given: "a scraper request for single page"
            def scraperRequestDto = new ScraperRequestDto(url: "https://example.com/products.html", category: "electronics")

        and: "a list of scraped products"
            List<Product> scrapedProducts = [
                    new Product(title: "Product 1", price: 100, url: "http://example.com/product1", category: "electronics"),
            ]

        ProductApiResponseDto apiResponseDto = new ProductApiResponseDto(
                items: [
                    new ProductApiResponseDto.ProductDetailsResponseDto(title: "Product 1", price: 100, url: "http://example.com/product1"),
                ]
        )

        when: "scraping products from single page"
            service.scrapeProducts(scraperRequestDto, false)

        then: "products should be scraped from the URL"
            1 * productsScraper.fetchProductsPage("https://example.com/products.json") >> apiResponseDto
            1 * productMapper.toProduct(apiResponseDto.items[0], "electronics", urlBuilder) >> scrapedProducts[0]
            1 * productRepository.findByUrl(scrapedProducts[0].url) >> Optional.of(scrapedProducts[0])
            0 * productRepository.save(_)
            0 * _

        and: "log should contain info about scraped products"
            assertLog(Level.INFO, "Finished scraping page: https://example.com/products")
    }

    def "Happy path, scrape products from single page and update if it already exists but with different price"() {
        given: "a scraper request for single page"
            def scraperRequestDto = new ScraperRequestDto(url: "https://example.com/products.html", category: "electronics")

        and: "a list of scraped products"
            List<Product> scrapedProducts = [
                    new Product(title: "Product 1", price: 150, url: "http://example.com/product1", category: "electronics"),
            ]

        ProductApiResponseDto apiResponseDto = new ProductApiResponseDto(
                items: [
                    new ProductApiResponseDto.ProductDetailsResponseDto(title: "Product 1", price: 150, url: "http://example.com/product1"),
                ]
        )

        when: "scraping products from single page"
            service.scrapeProducts(scraperRequestDto, false)

        then: "products should be scraped from the URL"
            1 * productsScraper.fetchProductsPage("https://example.com/products.json") >> apiResponseDto
            1 * productMapper.toProduct(apiResponseDto.items[0], "electronics", urlBuilder) >> scrapedProducts[0]
            1 * productRepository.findByUrl(scrapedProducts[0].url) >> Optional.of(new Product(title: "Product 1", price: 100, url: "http://example.com/product1", category: "electronics"))
            1 * productRepository.save(scrapedProducts[0]) >> scrapedProducts[0]
            0 * _

        and: "log should contain info about scraped products"
            assertLog(Level.INFO, "Finished scraping page: https://example.com/products")
    }

    def "Happy path, scrape products from multiple pages and save if they do not exist"() {
        given: "a scraper request for multiple pages"
            def scraperRequestDto = new ScraperRequestDto(url: "https://example.com/products.html", category: "electronics")

        and: "a list of scraped products for page 1 and page 2"
            List<Product> scrapedProductsPage1 = [
                    new Product(title: "Product 1", price: 100, url: "http://example.com/product1", category: "electronics"),
            ]
            List<Product> scrapedProductsPage2 = [
                    new Product(title: "Product 2", price: 200, url: "http://example.com/product2", category: "electronics"),
            ]

            ProductApiResponseDto apiResponseDtoPage1 = new ProductApiResponseDto(
                    items: [
                            new ProductApiResponseDto.ProductDetailsResponseDto(title: "Product 1", price: 100, url: "http://example.com/product1"),
                    ],
                    page: new ProductApiResponseDto.PageDetailsResponseDto(totalPages: 2, currentPage: 1)
            )

            ProductApiResponseDto apiResponseDtoPage2 = new ProductApiResponseDto(
                    items: [
                            new ProductApiResponseDto.ProductDetailsResponseDto(title: "Product 2", price: 200, url: "http://example.com/product2"),
                    ],
                    page: new ProductApiResponseDto.PageDetailsResponseDto(totalPages: 2, currentPage: 2)
            )

        when: "scraping products from multiple pages"
            service.scrapeProducts(scraperRequestDto, true)

        then: "products should be scraped from all pages"
            2 * productsScraper.fetchProductsPage("https://example.com/products.json") >> apiResponseDtoPage1
            1 * productsScraper.fetchProductsPage("https://example.com/products.json?page=2") >> apiResponseDtoPage2
            1 * productMapper.toProduct(apiResponseDtoPage1.items[0], "electronics", urlBuilder) >> scrapedProductsPage1[0]
            1 * productMapper.toProduct(apiResponseDtoPage2.items[0], "electronics", urlBuilder) >> scrapedProductsPage2[0]
            [scrapedProductsPage1[0], scrapedProductsPage2[0]].each {
                1 * productRepository.findByUrl(it.url) >> Optional.empty()
                1 * productRepository.save(it) >> it
            }
            0 * _

        and:
        assertLog(Level.INFO, "Found 2 pages to scrape")
    }

    def "Unhappy path, should log error if scraping fails"() {
        given: "a scraper request for single page"
            def scraperRequestDto = new ScraperRequestDto(url: "https://example.com/products.html", category: "electronics")

        when: "scraping products from single page"
            service.scrapeProducts(scraperRequestDto, false)

        then: "scraper throws exception"
            1 * productsScraper.fetchProductsPage("https://example.com/products.json") >> { throw new Exception("Scraping failed") }
            0 * _

        and: "log should contain error about failed scraping"
            assertLog(Level.ERROR, "Failed scraping page https://example.com/products.html")
    }

    def "Unhappy path, should log error if getting number of pages fail"() {
        given: "a URL to check"
            String url = "https://example.com/products.html"
            ScraperRequestDto scraperRequestDto = new ScraperRequestDto(url: url, category: "electronics")

            // create a ProductApiResponseDto with page details to trigger multiple page scraping
            ProductApiResponseDto apiResponseDto = new ProductApiResponseDto(
                    items: [],
                    page: new ProductApiResponseDto.PageDetailsResponseDto(totalPages: 0, currentPage: 0)
            )

        when: "getting number of web pages"
            service.scrapeProducts(scraperRequestDto, false)

        then: "scraper throws exception"
            1 * productsScraper.fetchProductsPage("https://example.com/products.json") >> apiResponseDto
            0 * _
    }
}

