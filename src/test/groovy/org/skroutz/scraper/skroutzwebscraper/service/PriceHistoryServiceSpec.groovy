package org.skroutz.scraper.skroutzwebscraper.service

import ch.qos.logback.classic.Level
import org.skroutz.scraper.skroutzwebscraper.base.WithLoggingBaseSpec
import org.skroutz.scraper.skroutzwebscraper.dto.PriceHistoryResponseDto
import org.skroutz.scraper.skroutzwebscraper.entity.PriceHistory
import org.skroutz.scraper.skroutzwebscraper.entity.Product
import org.skroutz.scraper.skroutzwebscraper.repository.PriceHistoryRepository
import org.skroutz.scraper.skroutzwebscraper.repository.ProductRepository
import org.skroutz.scraper.skroutzwebscraper.scraper.PriceHistoryScraper
import org.skroutz.scraper.skroutzwebscraper.utils.UrlBuilder
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.SliceImpl
import org.springframework.transaction.support.TransactionTemplate
import spock.lang.Subject

import java.sql.Timestamp
import java.util.function.Consumer

class PriceHistoryServiceSpec extends WithLoggingBaseSpec {

    ProductRepository productRepository = Mock()
    PriceHistoryRepository historyRepo = Mock()
    PriceHistoryScraper scraper = Mock()
    UrlBuilder urlBuilder = Mock()
    TransactionTemplate transactionTemplate = Mock()

    @Subject
    PriceHistoryService priceHistoryService

    def setup() {
        // Initialize service with mocked dependencies and 0 delay for fast tests
        priceHistoryService = new PriceHistoryService(
                productRepository,
                historyRepo,
                scraper,
                urlBuilder,
                transactionTemplate,
        )

        priceHistoryService.delayMs = 0

        // Setup TransactionTemplate to immediately execute the callback logic
        transactionTemplate.executeWithoutResult(_ as Consumer) >> { Consumer c ->
            c.accept(null)
        }
    }

    def "Should process products in slices and save new history"() {
        given: "A slice containing one valid product"
            Product product = Product.builder()
                    .id(1L)
                    .url("https://example.com/p1")
                    .priceHistoryParsed(false)
                    .build()

            def slice = new SliceImpl([product], PageRequest.of(0, 100), false)

        and: "A scraper response with one new data point"
            def timestamp = 1704067200L // 2024-01-01
            def response = PriceHistoryResponseDto.builder()
                    .minPrice(PriceHistoryResponseDto.MetricDataDto.builder()
                            .graphData(PriceHistoryResponseDto.GraphDataDto.builder()
                                    .all(PriceHistoryResponseDto.TimePeriodDto.builder()
                                            .values([PriceHistoryResponseDto.DataPointDto.builder()
                                                             .timestamp(timestamp)
                                                             .value(10.5G)
                                                             .shopName("Store A")
                                                             .build()])
                                            .build())
                                    .build())
                            .build())
                    .build()

        when: "Service is executed"
            priceHistoryService.fetchPriceHistoryForProducts()

        then: "Orchestration flow is followed"
            1 * productRepository.findAllByPriceHistoryParsed(false, PageRequest.of(0, 100)) >> slice
            1 * urlBuilder.buildPriceGraphApiUrl("https://example.com/p1") >> "http://api.v1/p1"
            1 * scraper.fetchPriceHistory("http://api.v1/p1") >> response

        and: "Data is filtered and saved"
            1 * historyRepo.findTopByProductIdOrderByPriceDateDesc(1L) >> null
            1 * historyRepo.saveAll({ List<PriceHistory> list -> list.size() == 1 })
            1 * productRepository.save({ it.id == 1L && it.priceHistoryParsed == true })

    }

    def "Should filter out data points older than the last recorded price"() {
        given: "An existing history record from Jan 10"
            Product product = Product.builder().id(1L).url("http://test.com").priceHistoryParsed(false).build()
            def existingHistory = PriceHistory.builder().priceDate(new Timestamp(1704844800000L)).build() // Jan 10
            def slice = new SliceImpl([product], PageRequest.of(0, 100), false)

        and: "A response with an old (Jan 5) and a new (Jan 15) data point"
            def oldTs = 1704412800L // Jan 5
            def newTs = 1705276800L // Jan 15
            def response = createResponseWithTimestamps([oldTs, newTs])

        when: "Service is executed"
            priceHistoryService.fetchPriceHistoryForProducts()

        then: "Mocks must be stubbed and verified in the same block for 1 * interactions"
            1 * productRepository.findAllByPriceHistoryParsed(false, _) >> slice
            1 * urlBuilder.buildPriceGraphApiUrl("http://test.com") >> "formatted-url"
            1 * scraper.fetchPriceHistory("formatted-url") >> response
            1 * historyRepo.findTopByProductIdOrderByPriceDateDesc(1L) >> existingHistory

            1 * historyRepo.saveAll({ List<PriceHistory> list ->
                list.size() == 1 && list[0].priceDate.time > existingHistory.priceDate.time
            })
            1 * productRepository.save({ it.id == 1L && it.priceHistoryParsed == true })
    }

    def "Should skip product and log warning when URL is empty"() {
        given: "A product with a blank URL"
            Product product = Product.builder().id(99L).url("").priceHistoryParsed(false).build()
            def slice = new SliceImpl([product], PageRequest.of(0, 100), false)

        when: "Service is executed"
            priceHistoryService.fetchPriceHistoryForProducts()

        then: "Scraper is never called"
            1 * productRepository.findAllByPriceHistoryParsed(false, _) >> slice
            0 * scraper.fetchPriceHistory(_)
            0 * historyRepo.saveAll(_)
    }

    def "Should mark product as parsed even if response has no data"() {
        given: "A product and an empty response"
            Product product = Product.builder().id(1L).url("url").build()
            def slice = new SliceImpl([product], PageRequest.of(0, 100), false)
            def emptyResponse = PriceHistoryResponseDto.builder().build()

        when: "Service is executed"
            priceHistoryService.fetchPriceHistoryForProducts()

        then: "Flow completes without saving history"
            1 * productRepository.findAllByPriceHistoryParsed(false, _) >> slice
            1 * urlBuilder.buildPriceGraphApiUrl("url") >> "api-url"
            1 * scraper.fetchPriceHistory("api-url") >> emptyResponse
            0 * historyRepo.saveAll(_)
            1 * productRepository.save({ it.priceHistoryParsed == true })
    }

    def "Should continue processing next product if one fails"() {
        given: "Two products"
            Product p1 = Product.builder().id(1L).url("url1").build()
            Product p2 = Product.builder().id(2L).url("url2").build()
            def slice = new SliceImpl([p1, p2], PageRequest.of(0, 100), false)

        when: "Service is executed"
            priceHistoryService.fetchPriceHistoryForProducts()

        then: "Both are attempted despite p1 failing"
            1 * productRepository.findAllByPriceHistoryParsed(false, _) >> slice

            // Interaction for p1
            1 * urlBuilder.buildPriceGraphApiUrl("url1") >> { throw new RuntimeException("Scraping Error") }

            // Interaction for p2 (still happens!)
            1 * urlBuilder.buildPriceGraphApiUrl("url2") >> "url2-api"
            1 * scraper.fetchPriceHistory("url2-api") >> PriceHistoryResponseDto.builder().build()
            1 * productRepository.save({ it.id == 2L })

        and: "Error is logged"
            assertLog(Level.ERROR, "Error processing product 1: Scraping Error")
    }

    private PriceHistoryResponseDto createResponseWithTimestamps(List<Long> timestamps) {
        def points = timestamps.collect { ts ->
            PriceHistoryResponseDto.DataPointDto.builder().timestamp(ts).value(10.0G).shopName("Shop").build()
        }
        return PriceHistoryResponseDto.builder()
                .minPrice(PriceHistoryResponseDto.MetricDataDto.builder()
                        .graphData(PriceHistoryResponseDto.GraphDataDto.builder()
                                .all(PriceHistoryResponseDto.TimePeriodDto.builder().values(points).build())
                                .build())
                        .build())
                .build()
    }
}
