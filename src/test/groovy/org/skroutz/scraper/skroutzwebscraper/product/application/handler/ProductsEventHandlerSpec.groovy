package org.skroutz.scraper.skroutzwebscraper.product.application.handler

import ch.qos.logback.classic.Level
import org.skroutz.scraper.skroutzwebscraper.base.WithLoggingBaseSpec
import org.skroutz.scraper.skroutzwebscraper.common.infrastructure.dto.events.PriceHistorySavedEvent
import org.skroutz.scraper.skroutzwebscraper.common.infrastructure.dto.events.SpecificationsScrapeResult
import org.skroutz.scraper.skroutzwebscraper.product.ProductSearchEvent
import org.skroutz.scraper.skroutzwebscraper.product.application.service.ProductPersistenceService
import org.skroutz.scraper.skroutzwebscraper.product.domain.entity.Product
import org.skroutz.scraper.skroutzwebscraper.product.domain.repository.ProductRepository
import org.skroutz.scraper.skroutzwebscraper.review.ReviewsSavedEvent
import org.skroutz.scraper.skroutzwebscraper.common.infrastructure.dto.api.ProductApiResponseDto
import org.skroutz.scraper.skroutzwebscraper.common.infrastructure.dto.events.ProductsScrapeResult
import org.springframework.context.ApplicationEventPublisher
import spock.lang.Subject

class ProductsEventHandlerSpec extends WithLoggingBaseSpec {

    ProductRepository productRepository = Mock(ProductRepository)
    ProductPersistenceService persistenceService = Mock(ProductPersistenceService)
    ApplicationEventPublisher eventPublisher = Mock(ApplicationEventPublisher)

    @Subject
    ProductsEventHandler handler = new ProductsEventHandler(productRepository, persistenceService, eventPublisher)

    def "Should mark product as reviews parsed when ReviewsSavedEvent is received"() {
        given: "an existing product"
            def product = new Product(id: 1L, title: "Product 1", url: "http://example.com/product1", reviewsParsed: false)

        when: "the reviews saved event is handled"
            handler.markReviewsProductAsScraped(new ReviewsSavedEvent(1L))

        then: "the product is flagged and saved"
            1 * productRepository.findById(1L) >> Optional.of(product)
            1 * productRepository.save(product) >> product
            0 * _

        and: "state and logs reflect the update"
            product.reviewsParsed
            assertLog(Level.INFO, "Marked product ID 1 as reviews parsed.")
    }

    def "Should throw if ReviewsSavedEvent references a missing product"() {
        when:
            handler.markReviewsProductAsScraped(new ReviewsSavedEvent(99L))

        then:
            1 * productRepository.findById(99L) >> Optional.empty()
            0 * _
            def exception = thrown(IllegalArgumentException)
            exception.message.contains("Product target entity missing ID: 99")
    }

    def "Should mark product as price history parsed when PriceHistorySavedEvent is received"() {
        given: "an existing product"
            def product = new Product(id: 2L, title: "Product 2", url: "http://example.com/product2", priceHistoryParsed: false)

        when: "the price history saved event is handled"
            handler.markPriceHistoryProductAsScraped(new PriceHistorySavedEvent(2L))

        then: "the product is flagged and saved"
            1 * productRepository.findById(2L) >> Optional.of(product)
            1 * productRepository.save(product) >> product
            0 * _

        and: "state and logs reflect the update"
            product.priceHistoryParsed
            assertLog(Level.INFO, "Marked product ID 2 as price history parsed.")
    }

    def "Should throw if PriceHistorySavedEvent references a missing product"() {
        when:
            handler.markPriceHistoryProductAsScraped(new PriceHistorySavedEvent(88L))

        then:
            1 * productRepository.findById(88L) >> Optional.empty()
            0 * _
            def exception = thrown(IllegalArgumentException)
            exception.message.contains("Product target entity missing ID: 88")
    }

    def "Should persist specifications results and publish search event"() {
        given: "scrape results for two products"
            def item1 = new SpecificationsScrapeResult.SpecificationsScrapeItem(1L, null, null, "Brand A", true)
            def item2 = new SpecificationsScrapeResult.SpecificationsScrapeItem(2L, null, null, "Brand B", false)
            def results = new SpecificationsScrapeResult([item1, item2])
            def searchEvent = new ProductSearchEvent([])

        when: "the specifications scrape result is handled"
            handler.parseSpecificationsEvent(results)

        then: "results are persisted and the returned search event is published"
            1 * persistenceService.saveSpecificationsResults(results) >> searchEvent
            1 * eventPublisher.publishEvent(searchEvent)
            0 * _

        and: "logs report the processed item count"
            assertLog(Level.INFO, "Processed specifications scrape results for 2 products.")
    }

    def "Should persist products scrape results when ProductsScrapeResult is received"() {
        given: "a products scrape result for a category"
            def itemDto = new ProductApiResponseDto.ProductDetailsResponseDto(title: "Product 1", price: 100, url: "http://example.com/product1")
            def apiResponseDto = new ProductApiResponseDto(items: [itemDto])
            def event = new ProductsScrapeResult(apiResponseDto, "electronics")

        when: "the products scrape result is handled"
            handler.handleProductsScrapeResultEvent(event)

        then: "persistence is invoked with the dto and category"
            1 * persistenceService.saveOrUpdateProducts(apiResponseDto, "electronics")
            0 * _

        and: "logs report the processed category"
            assertLog(Level.INFO, "Processed products scrape results for category: electronics")
    }
}
