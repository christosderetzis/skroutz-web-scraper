package org.skroutz.scraper.skroutzwebscraper.product.application.service

import ch.qos.logback.classic.Level

// Required for Mock repository return definitions

// Needed if you directly invoke updateField checks manually
import org.skroutz.scraper.skroutzwebscraper.base.WithLoggingBaseSpec
import org.skroutz.scraper.skroutzwebscraper.product.domain.entity.Product
import org.skroutz.scraper.skroutzwebscraper.product.domain.repository.ProductRepository
import org.skroutz.scraper.skroutzwebscraper.product.infrastructure.mapper.ProductMapper
import org.skroutz.scraper.skroutzwebscraper.scraping.infrastructure.dto.api.ProductApiResponseDto

// Adjusted target package domain guess

import org.skroutz.scraper.skroutzwebscraper.scraping.infrastructure.utils.UrlBuilder
import spock.lang.Subject

class ProductPersistenceServiceSpec extends WithLoggingBaseSpec {

    ProductRepository productRepository = Mock(ProductRepository)
    ProductMapper productMapper = Mock(ProductMapper)
    UrlBuilder urlBuilder = new UrlBuilder("https://example.com")

    @Subject
    ProductPersistenceService service = new ProductPersistenceService(productRepository, productMapper, urlBuilder)

    def "Should map items and save a new product if it does not exist"() {
        given: "an API response containing an un-persisted product"
            def itemDto = new ProductApiResponseDto.ProductDetailsResponseDto(title: "Product 1", price: 100, url: "http://example.com/product1")
            def apiResponseDto = new ProductApiResponseDto(items: [itemDto])
            def mappedProduct = new Product(title: "Product 1", price: 100, url: "http://example.com/product1", category: "electronics")

        when: "processing persistence payload"
            service.saveOrUpdateProducts(apiResponseDto, "electronics")

        then: "mapper converts dto to entity and database saves a new entry"
            1 * productMapper.toProduct(itemDto, "electronics", urlBuilder) >> mappedProduct
            1 * productRepository.findByUrl(mappedProduct.url) >> Optional.empty()
            1 * productRepository.save(mappedProduct) >> mappedProduct
            0 * _
    }

    def "Should ignore updates and not save if a matching product exists with identical attributes"() {
        given: "an existing product matches incoming scraped data exactly"
            def itemDto = new ProductApiResponseDto.ProductDetailsResponseDto(title: "Product 1", price: 100, url: "http://example.com/product1")
            def apiResponseDto = new ProductApiResponseDto(items: [itemDto])
            
            def scrapedProduct = new Product(title: "Product 1", price: 100, url: "http://example.com/product1", category: "electronics", rating: 4.5)
            def existingProduct = new Product(title: "Product 1", price: 100, url: "http://example.com/product1", category: "electronics", rating: 4.5)

        when: "processing persistence payload"
            service.saveOrUpdateProducts(apiResponseDto, "electronics")

        then: "no database save mutation operation is executed because field differences evaluate false"
            1 * productMapper.toProduct(itemDto, "electronics", urlBuilder) >> scrapedProduct
            1 * productRepository.findByUrl(scrapedProduct.url) >> Optional.of(existingProduct)
            0 * productRepository.save(_)
            0 * _
    }

    def "Should apply updates and mutate state if an existing product contains modified fields"() {
        given: "incoming item has a modified price and rating status"
            def itemDto = new ProductApiResponseDto.ProductDetailsResponseDto(title: "Product 1", price: 150, url: "http://example.com/product1")
            def apiResponseDto = new ProductApiResponseDto(items: [itemDto])
            
            def scrapedProduct = new Product(title: "Product 1", price: 150, rating: 4.8, url: "http://example.com/product1", category: "electronics")
            def existingProduct = new Product(title: "Product 1", price: 100, rating: 4.2, url: "http://example.com/product1", category: "electronics")

        when: "processing persistence payload"
            service.saveOrUpdateProducts(apiResponseDto, "electronics")

        then: "dirty tracking updates fields and pushes database save call"
            1 * productMapper.toProduct(itemDto, "electronics", urlBuilder) >> scrapedProduct
            1 * productRepository.findByUrl(scrapedProduct.url) >> Optional.of(existingProduct)
            1 * productRepository.save(existingProduct) >> existingProduct
            0 * _
            
        and: "existing entity instance variables are properly reassigned values"
            existingProduct.price == 150
            existingProduct.rating == 4.8
    }

    def "Should skip processing completely if a scraped product returns missing title or URL fields"() {
        given: "an API response containing an unmappable product criteria"
            def itemDto = new ProductApiResponseDto.ProductDetailsResponseDto(title: null, url: null)
            def apiResponseDto = new ProductApiResponseDto(items: [itemDto])
            def invalidProduct = new Product(title: null, url: null)

        when: "processing persistence payload"
            service.saveOrUpdateProducts(apiResponseDto, "electronics")

        then: "system discards operational loop processing rules gracefully"
            1 * productMapper.toProduct(itemDto, "electronics", urlBuilder) >> invalidProduct
            0 * productRepository.findByUrl(_)
            0 * productRepository.save(_)
            0 * _

        and: "warning diagnostics are logged safely"
            assertLog(Level.WARN, "Skipping invalid product due to missing title/url")
    }
}