package org.skroutz.scraper.skroutzwebscraper.service

import ch.qos.logback.classic.Level
import org.skroutz.scraper.skroutzwebscraper.base.WithLoggingBaseSpec
import org.skroutz.scraper.skroutzwebscraper.dto.PriceHistoryResponseDto
import org.skroutz.scraper.skroutzwebscraper.entity.PriceHistory
import org.skroutz.scraper.skroutzwebscraper.entity.Product
import org.skroutz.scraper.skroutzwebscraper.repository.PriceHistoryRepository
import org.skroutz.scraper.skroutzwebscraper.repository.ProductRepository
import org.skroutz.scraper.skroutzwebscraper.scraper.PriceHistoryScraper
import spock.lang.Subject

import java.sql.Timestamp

class PriceHistoryTxServiceSpec extends WithLoggingBaseSpec {

    PriceHistoryScraper priceHistoryScraper = Mock()
    PriceHistoryRepository priceHistoryRepository = Mock()
    ProductRepository productRepository = Mock()

    @Subject
    PriceHistoryTxService priceHistoryTxService = new PriceHistoryTxService(
            priceHistoryScraper,
            priceHistoryRepository,
            productRepository
    )

    def "Happy path, process single product with new price histories"() {
        given: "a product and URL"
            Product product = Product.builder()
                    .id(1L)
                    .title("Test Product")
                    .priceHistoryParsed(false)
                    .build()
            String url = "https://example.com/price_graph.json"

        and: "price history response with new data"
            def timestamp1 = 1704067200L // 2024-01-01 00:00:00 UTC
            def timestamp2 = 1705276800L // 2024-01-15 00:00:00 UTC

            def dataPoints = [
                    PriceHistoryResponseDto.DataPointDto.builder()
                            .timestamp(timestamp1)
                            .value(100.00G)
                            .shopName("Shop A")
                            .build(),
                    PriceHistoryResponseDto.DataPointDto.builder()
                            .timestamp(timestamp2)
                            .value(95.50G)
                            .shopName("Shop B")
                            .build()
            ]

            def response = PriceHistoryResponseDto.builder()
                    .minPrice(PriceHistoryResponseDto.MetricDataDto.builder()
                            .graphData(PriceHistoryResponseDto.GraphDataDto.builder()
                                    .all(PriceHistoryResponseDto.TimePeriodDto.builder()
                                            .values(dataPoints)
                                            .build())
                                    .build())
                            .build())
                    .build()

        when: "processSingleProduct is called"
            priceHistoryTxService.processSingleProduct(product, url)

        then: "price history is fetched and saved"
            1 * priceHistoryScraper.fetchPriceHistory(url) >> response
            1 * priceHistoryRepository.findTopByProductIdOrderByPriceDateDesc(1L) >> null
            1 * priceHistoryRepository.saveAll({ List<PriceHistory> histories ->
                histories.size() == 2 &&
                        histories[0].productId == 1L &&
                        histories[0].price == 100.00G &&
                        histories[0].storeName == "Shop A" &&
                        histories[1].productId == 1L &&
                        histories[1].price == 95.50G &&
                        histories[1].storeName == "Shop B"
            })
            1 * productRepository.save({ it.priceHistoryParsed == true })
            0 * _

        and: "success is logged"
            assertLog(Level.INFO, "Fetching and saving price history for product ID: 1")
            assertLog(Level.INFO, "Saved 2 price history records for product ID: 1")
    }

    def "Happy path, filter out old price histories"() {
        given: "a product with existing price history"
            Product product = Product.builder()
                    .id(1L)
                    .title("Test Product")
                    .priceHistoryParsed(false)
                    .build()
            String url = "https://example.com/price_graph.json"

        and: "existing price history from Jan 10"
            def existingPriceHistory = PriceHistory.builder()
                    .id(1L)
                    .productId(1L)
                    .priceDate(new Timestamp(1704844800000L)) // 2024-01-10 00:00:00 UTC
                    .price(98.00G)
                    .storeName("Old Shop")
                    .build()

        and: "response with mixed old and new data"
            def oldTimestamp = 1704412800L // 2024-01-05 00:00:00 UTC
            def newTimestamp = 1705276800L // 2024-01-15 00:00:00 UTC

            def dataPoints = [
                    PriceHistoryResponseDto.DataPointDto.builder()
                            .timestamp(oldTimestamp)
                            .value(100.00G)
                            .shopName("Shop A")
                            .build(),
                    PriceHistoryResponseDto.DataPointDto.builder()
                            .timestamp(newTimestamp)
                            .value(95.50G)
                            .shopName("Shop B")
                            .build()
            ]

            def response = PriceHistoryResponseDto.builder()
                    .minPrice(PriceHistoryResponseDto.MetricDataDto.builder()
                            .graphData(PriceHistoryResponseDto.GraphDataDto.builder()
                                    .all(PriceHistoryResponseDto.TimePeriodDto.builder()
                                            .values(dataPoints)
                                            .build())
                                    .build())
                            .build())
                    .build()

        when: "processSingleProduct is called"
            priceHistoryTxService.processSingleProduct(product, url)

        then: "only new price history is saved"
            1 * priceHistoryScraper.fetchPriceHistory(url) >> response
            1 * priceHistoryRepository.findTopByProductIdOrderByPriceDateDesc(1L) >> existingPriceHistory
            1 * priceHistoryRepository.saveAll({ List<PriceHistory> histories ->
                histories.size() == 1 &&
                        histories[0].price == 95.50G &&
                        histories[0].storeName == "Shop B"
            })
            1 * productRepository.save({ it.priceHistoryParsed == true })
            0 * _
    }

    def "Happy path, no new price histories - #scenario"() {
        given: "a product and URL"
            Product product = Product.builder()
                    .id(1L)
                    .title("Test Product")
                    .priceHistoryParsed(false)
                    .build()
            String url = "https://example.com/price_graph.json"

        when: "processSingleProduct is called"
            priceHistoryTxService.processSingleProduct(product, url)

        then: "no price history is saved"
            1 * priceHistoryScraper.fetchPriceHistory(url) >> response
            1 * productRepository.save({ it.priceHistoryParsed == true })
            0 * _

        and: "warning is logged"
            assertLog(Level.WARN, "No new price history data available for product ID: 1")

        where: "different null scenarios in response structure"
            scenario            | response
            "minPrice is null"  | PriceHistoryResponseDto.builder()
                                      .minPrice(null)
                                      .build()
            "graphData is null" | PriceHistoryResponseDto.builder()
                                      .minPrice(PriceHistoryResponseDto.MetricDataDto.builder()
                                              .graphData(null)
                                              .build())
                                      .build()
            "all is null"       | PriceHistoryResponseDto.builder()
                                      .minPrice(PriceHistoryResponseDto.MetricDataDto.builder()
                                              .graphData(PriceHistoryResponseDto.GraphDataDto.builder()
                                                      .all(null)
                                                      .build())
                                              .build())
                                      .build()
            "values is null"    | PriceHistoryResponseDto.builder()
                                      .minPrice(PriceHistoryResponseDto.MetricDataDto.builder()
                                              .graphData(PriceHistoryResponseDto.GraphDataDto.builder()
                                                      .all(PriceHistoryResponseDto.TimePeriodDto.builder()
                                                              .values(null)
                                                              .build())
                                                      .build())
                                              .build())
                                      .build()
    }

    def "Happy path, empty values list"() {
        given: "a product and URL"
            Product product = Product.builder()
                    .id(1L)
                    .title("Test Product")
                    .priceHistoryParsed(false)
                    .build()
            String url = "https://example.com/price_graph.json"

        and: "response with empty values list"
            def response = PriceHistoryResponseDto.builder()
                    .minPrice(PriceHistoryResponseDto.MetricDataDto.builder()
                            .graphData(PriceHistoryResponseDto.GraphDataDto.builder()
                                    .all(PriceHistoryResponseDto.TimePeriodDto.builder()
                                            .values([])
                                            .build())
                                    .build())
                            .build())
                    .build()

        when: "processSingleProduct is called"
            priceHistoryTxService.processSingleProduct(product, url)

        then: "no price history is saved"
            1 * priceHistoryScraper.fetchPriceHistory(url) >> response
            1 * priceHistoryRepository.findTopByProductIdOrderByPriceDateDesc(1L) >> null
            1 * productRepository.save({ it.priceHistoryParsed == true })
            0 * _

        and: "warning is logged"
            assertLog(Level.WARN, "No new price history data available for product ID: 1")
    }

    def "Happy path, all price histories filtered out as old"() {
        given: "a product with existing price history"
            Product product = Product.builder()
                    .id(1L)
                    .title("Test Product")
                    .priceHistoryParsed(false)
                    .build()
            String url = "https://example.com/price_graph.json"

        and: "existing price history from Jan 20"
            def existingPriceHistory = PriceHistory.builder()
                    .id(1L)
                    .productId(1L)
                    .priceDate(new Timestamp(1705708800000L)) // 2024-01-20 00:00:00 UTC
                    .price(90.00G)
                    .storeName("Latest Shop")
                    .build()

        and: "response with only old data"
            def oldTimestamp1 = 1704412800L // 2024-01-05 00:00:00 UTC
            def oldTimestamp2 = 1704844800L // 2024-01-10 00:00:00 UTC

            def dataPoints = [
                    PriceHistoryResponseDto.DataPointDto.builder()
                            .timestamp(oldTimestamp1)
                            .value(100.00G)
                            .shopName("Shop A")
                            .build(),
                    PriceHistoryResponseDto.DataPointDto.builder()
                            .timestamp(oldTimestamp2)
                            .value(95.00G)
                            .shopName("Shop B")
                            .build()
            ]

            def response = PriceHistoryResponseDto.builder()
                    .minPrice(PriceHistoryResponseDto.MetricDataDto.builder()
                            .graphData(PriceHistoryResponseDto.GraphDataDto.builder()
                                    .all(PriceHistoryResponseDto.TimePeriodDto.builder()
                                            .values(dataPoints)
                                            .build())
                                    .build())
                            .build())
                    .build()

        when: "processSingleProduct is called"
            priceHistoryTxService.processSingleProduct(product, url)

        then: "no price history is saved as all are filtered"
            1 * priceHistoryScraper.fetchPriceHistory(url) >> response
            1 * priceHistoryRepository.findTopByProductIdOrderByPriceDateDesc(1L) >> existingPriceHistory
            1 * productRepository.save({ it.priceHistoryParsed == true })
            0 * _

        and: "warning is logged"
            assertLog(Level.WARN, "No new price history data available for product ID: 1")
    }

    def "Happy path, product is marked as parsed even when no data"() {
        given: "a product and URL"
            Product product = Product.builder()
                    .id(1L)
                    .title("Test Product")
                    .priceHistoryParsed(false)
                    .build()
            String url = "https://example.com/price_graph.json"

        and: "response with no data"
            def response = PriceHistoryResponseDto.builder().build()

        when: "processSingleProduct is called"
            priceHistoryTxService.processSingleProduct(product, url)

        then: "product is marked as parsed"
            1 * priceHistoryScraper.fetchPriceHistory(url) >> response
            0 * priceHistoryRepository.findTopByProductIdOrderByPriceDateDesc(_)
            1 * productRepository.save({ Product p ->
                p.priceHistoryParsed == true && p.id == 1L
            })
            0 * _
    }

    def "Happy path, multiple data points with same timestamp"() {
        given: "a product and URL"
            Product product = Product.builder()
                    .id(1L)
                    .title("Test Product")
                    .priceHistoryParsed(false)
                    .build()
            String url = "https://example.com/price_graph.json"

        and: "response with multiple data points at same timestamp"
            def timestamp = 1704067200L // 2024-01-01 00:00:00 UTC

            def dataPoints = [
                    PriceHistoryResponseDto.DataPointDto.builder()
                            .timestamp(timestamp)
                            .value(100.00G)
                            .shopName("Shop A")
                            .build(),
                    PriceHistoryResponseDto.DataPointDto.builder()
                            .timestamp(timestamp)
                            .value(95.50G)
                            .shopName("Shop B")
                            .build()
            ]

            def response = PriceHistoryResponseDto.builder()
                    .minPrice(PriceHistoryResponseDto.MetricDataDto.builder()
                            .graphData(PriceHistoryResponseDto.GraphDataDto.builder()
                                    .all(PriceHistoryResponseDto.TimePeriodDto.builder()
                                            .values(dataPoints)
                                            .build())
                                    .build())
                            .build())
                    .build()

        when: "processSingleProduct is called"
            priceHistoryTxService.processSingleProduct(product, url)

        then: "both price histories are saved"
            1 * priceHistoryScraper.fetchPriceHistory(url) >> response
            1 * priceHistoryRepository.findTopByProductIdOrderByPriceDateDesc(1L) >> null
            1 * priceHistoryRepository.saveAll({ List<PriceHistory> histories ->
                histories.size() == 2
            })
            1 * productRepository.save({ it.priceHistoryParsed == true })
            0 * _
    }
}
