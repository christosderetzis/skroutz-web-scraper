package org.skroutz.scraper.skroutzwebscraper.processing.service

import org.skroutz.scraper.skroutzwebscraper.processing.mapper.ProductMapper
import org.skroutz.scraper.skroutzwebscraper.processing.repository.ProductRepository
import org.skroutz.scraper.skroutzwebscraper.scraping.ScrapingService
import spock.lang.Specification
import spock.lang.Subject

class ProductsServiceSpec extends Specification {

    ScrapingService scrapingService = Mock(ScrapingService)
    ProductMapper productMapper = Mock(ProductMapper)
    ProductRepository productRepository = Mock(ProductRepository)

    @Subject
    ProductsService service = new ProductsService(scrapingService, productRepository, productMapper)

    def "Happy path, should delegate to scraping service"() {
        given: "a URL to scrape"
            String url = "http://example.com/products"

        when: "scraping and saving products"
            service.scrapeAndSaveProducts(url)

        then: "scraping service is called"
            1 * scrapingService.scrapeProducts(url)
            0 * _
    }

    def "Happy path, should return number of web pages"() {
        given: "a URL to check"
            String url = "http://example.com/products"

        when: "getting number of web pages"
            int pages = service.getNumberOfWebPages(url)

        then: "should return the number of pages from the scraping service"
            1 * scrapingService.getNumberOfPages(url) >> 5
            0 * _

        and: "should return correct number of pages"
            pages == 5
    }

    def "Unhappy path, should log error if getting number of pages fails"() {
        given: "a URL to check"
            String url = "http://example.com/products"

        when: "getting number of web pages"
            service.getNumberOfWebPages(url)

        then: "scraping service throws exception"
            1 * scrapingService.getNumberOfPages(url) >> { throw new Exception("Failed to get pages") }
            0 * _

        and:
            def ex = thrown(RuntimeException)
            ex.message.contains("Failed to get number of web pages")
    }
}
