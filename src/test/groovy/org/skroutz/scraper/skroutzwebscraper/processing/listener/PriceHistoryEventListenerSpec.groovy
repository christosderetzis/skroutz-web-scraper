package org.skroutz.scraper.skroutzwebscraper.processing.listener

import org.skroutz.scraper.skroutzwebscraper.processing.entity.PriceHistory
import org.skroutz.scraper.skroutzwebscraper.processing.entity.Product
import org.skroutz.scraper.skroutzwebscraper.processing.repository.PriceHistoryRepository
import org.skroutz.scraper.skroutzwebscraper.processing.repository.ProductRepository
import org.skroutz.scraper.skroutzwebscraper.scraping.dto.PriceHistoryResponseDto
import org.skroutz.scraper.skroutzwebscraper.scraping.event.PriceHistoryScrapedEvent
import spock.lang.Specification
import spock.lang.Subject

import java.sql.Timestamp

class PriceHistoryEventListenerSpec extends Specification {

    PriceHistoryRepository priceHistoryRepository = Mock()
    ProductRepository productRepository = Mock()

    @Subject
    PriceHistoryEventListener listener = new PriceHistoryEventListener(priceHistoryRepository, productRepository)

    def "Happy path - extracts and saves new price histories, marks product as parsed"() {
        given: "a product and price history response with data points"
            def product = Product.builder()
                    .id(1L)
                    .title("Test Product")
                    .url("http://example.com/product")
                    .priceHistoryParsed(false)
                    .build()

            def dataPoint1 = PriceHistoryResponseDto.DataPointDto.builder()
                    .shopName("Shop A")
                    .timestamp(1700000000L)
                    .value(new BigDecimal("99.99"))
                    .build()

            def dataPoint2 = PriceHistoryResponseDto.DataPointDto.builder()
                    .shopName("Shop B")
                    .timestamp(1700100000L)
                    .value(new BigDecimal("89.99"))
                    .build()

            def response = PriceHistoryResponseDto.builder()
                    .minPrice(PriceHistoryResponseDto.MetricDataDto.builder()
                            .graphData(PriceHistoryResponseDto.GraphDataDto.builder()
                                    .all(PriceHistoryResponseDto.TimePeriodDto.builder()
                                            .values([dataPoint1, dataPoint2])
                                            .build())
                                    .build())
                            .build())
                    .build()

            def event = new PriceHistoryScrapedEvent(1L, response)

        when: "the event is handled"
            listener.handlePriceHistoryScraped(event)

        then: "product is looked up"
            1 * productRepository.findById(1L) >> Optional.of(product)

        and: "last recorded price history is checked"
            1 * priceHistoryRepository.findTopByProductIdOrderByPriceDateDesc(1L) >> null

        and: "new price histories are saved"
            1 * priceHistoryRepository.saveAll({ List<PriceHistory> histories ->
                histories.size() == 2 &&
                histories[0].productId == 1L &&
                histories[0].price == new BigDecimal("99.99") &&
                histories[0].storeName == "Shop A" &&
                histories[1].productId == 1L &&
                histories[1].price == new BigDecimal("89.99") &&
                histories[1].storeName == "Shop B"
            })

        and: "product is marked as price history parsed and saved"
            1 * productRepository.save({ Product p ->
                p.id == 1L && p.priceHistoryParsed == true
            })
    }

    def "Null response data - saves no price histories, still marks product as parsed"() {
        given: "a product and a response with null min price"
            def product = Product.builder()
                    .id(1L)
                    .title("Test Product")
                    .url("http://example.com/product")
                    .priceHistoryParsed(false)
                    .build()

            def response = PriceHistoryResponseDto.builder()
                    .minPrice(null)
                    .build()

            def event = new PriceHistoryScrapedEvent(1L, response)

        when: "the event is handled"
            listener.handlePriceHistoryScraped(event)

        then: "product is looked up"
            1 * productRepository.findById(1L) >> Optional.of(product)

        and: "no price histories are saved"
            0 * priceHistoryRepository.saveAll(_)

        and: "product is still marked as price history parsed and saved"
            1 * productRepository.save({ Product p ->
                p.id == 1L && p.priceHistoryParsed == true
            })
    }

    def "Filters out already-recorded dates"() {
        given: "a product with existing price history"
            def product = Product.builder()
                    .id(1L)
                    .title("Test Product")
                    .url("http://example.com/product")
                    .priceHistoryParsed(false)
                    .build()

            def lastRecorded = PriceHistory.builder()
                    .productId(1L)
                    .priceDate(new Timestamp(1700000000L * 1000))
                    .build()

        and: "a response with data points before and after the last recorded date"
            def oldDataPoint = PriceHistoryResponseDto.DataPointDto.builder()
                    .shopName("Shop A")
                    .timestamp(1699999000L)
                    .value(new BigDecimal("109.99"))
                    .build()

            def sameDataPoint = PriceHistoryResponseDto.DataPointDto.builder()
                    .shopName("Shop A")
                    .timestamp(1700000000L)
                    .value(new BigDecimal("99.99"))
                    .build()

            def newDataPoint = PriceHistoryResponseDto.DataPointDto.builder()
                    .shopName("Shop B")
                    .timestamp(1700200000L)
                    .value(new BigDecimal("79.99"))
                    .build()

            def response = PriceHistoryResponseDto.builder()
                    .minPrice(PriceHistoryResponseDto.MetricDataDto.builder()
                            .graphData(PriceHistoryResponseDto.GraphDataDto.builder()
                                    .all(PriceHistoryResponseDto.TimePeriodDto.builder()
                                            .values([oldDataPoint, sameDataPoint, newDataPoint])
                                            .build())
                                    .build())
                            .build())
                    .build()

            def event = new PriceHistoryScrapedEvent(1L, response)

        when: "the event is handled"
            listener.handlePriceHistoryScraped(event)

        then: "product is looked up"
            1 * productRepository.findById(1L) >> Optional.of(product)

        and: "last recorded price history is returned"
            1 * priceHistoryRepository.findTopByProductIdOrderByPriceDateDesc(1L) >> lastRecorded

        and: "only the new data point after the last recorded date is saved"
            1 * priceHistoryRepository.saveAll({ List<PriceHistory> histories ->
                histories.size() == 1 &&
                histories[0].productId == 1L &&
                histories[0].price == new BigDecimal("79.99") &&
                histories[0].storeName == "Shop B"
            })

        and: "product is marked as price history parsed"
            1 * productRepository.save({ Product p ->
                p.id == 1L && p.priceHistoryParsed == true
            })
    }

    def "Product not found - throws IllegalStateException"() {
        given: "an event for a non-existent product"
            def response = PriceHistoryResponseDto.builder().build()
            def event = new PriceHistoryScrapedEvent(999L, response)

        when: "the event is handled"
            listener.handlePriceHistoryScraped(event)

        then: "product is not found"
            1 * productRepository.findById(999L) >> Optional.empty()

        and: "an IllegalStateException is thrown"
            def ex = thrown(IllegalStateException)
            ex.message.contains("Product not found: 999")

        and: "no price histories are saved"
            0 * priceHistoryRepository.findTopByProductIdOrderByPriceDateDesc(_)
            0 * priceHistoryRepository.saveAll(_)
            0 * productRepository.save(_)
    }
}
