package org.skroutz.scraper.skroutzwebscraper.processing.listener

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.skroutz.scraper.skroutzwebscraper.processing.entity.Product
import org.skroutz.scraper.skroutzwebscraper.processing.repository.ProductRepository
import org.skroutz.scraper.skroutzwebscraper.scraping.event.SpecificationsScrapedEvent
import spock.lang.Specification
import spock.lang.Subject

class SpecificationsEventListenerSpec extends Specification {

    ProductRepository productRepository = Mock()

    @Subject
    SpecificationsEventListener listener = new SpecificationsEventListener(productRepository)

    def "Happy path - sets specifications and marks product as parsed"() {
        given: "a product and specifications JSON"
            def product = Product.builder()
                    .id(1L)
                    .title("Test Product")
                    .url("http://example.com/product")
                    .specificationsParsed(false)
                    .build()

            JsonNode specifications = new ObjectMapper().readTree('{"key": "value", "weight": "1.5kg"}')
            def event = new SpecificationsScrapedEvent(1L, specifications)

        when: "the event is handled"
            listener.handleSpecificationsScraped(event)

        then: "product is looked up"
            1 * productRepository.findById(1L) >> Optional.of(product)

        and: "product is saved with specifications set and marked as parsed"
            1 * productRepository.save({ Product p ->
                p.id == 1L &&
                p.specifications == specifications &&
                p.specificationsParsed == true
            })
    }

    def "Product not found - throws IllegalStateException"() {
        given: "an event for a non-existent product"
            JsonNode specifications = new ObjectMapper().readTree('{"key": "value"}')
            def event = new SpecificationsScrapedEvent(999L, specifications)

        when: "the event is handled"
            listener.handleSpecificationsScraped(event)

        then: "product is not found"
            1 * productRepository.findById(999L) >> Optional.empty()

        and: "an IllegalStateException is thrown"
            def ex = thrown(IllegalStateException)
            ex.message.contains("Product not found: 999")

        and: "no product is saved"
            0 * productRepository.save(_)
    }
}
