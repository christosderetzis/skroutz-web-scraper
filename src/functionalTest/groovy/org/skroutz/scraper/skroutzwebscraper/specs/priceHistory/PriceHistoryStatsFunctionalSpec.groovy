package org.skroutz.scraper.skroutzwebscraper.specs.priceHistory

import org.skroutz.scraper.skroutzwebscraper.product.domain.entity.Product
import org.skroutz.scraper.skroutzwebscraper.utils.base.BaseFunctionalSpec
import org.skroutz.scraper.skroutzwebscraper.utils.base.PriceHistoryRow
import org.skyscreamer.jsonassert.JSONAssert
import org.skyscreamer.jsonassert.JSONCompareMode

class PriceHistoryStatsFunctionalSpec extends BaseFunctionalSpec {

    def "404 - product has no price history"() {
        given: "a saved product with no price history rows"
            Product product = seedProduct("No history product", "no-history-stats-product")

        when: "requesting the price stats"
            def response = webActor.getPriceStats(product.id)

        then: "the response is 404 with the product not found error"
            response.expectStatus().isNotFound()
            String body = response.expectBody(String).returnResult().getResponseBody()
            JSONAssert.assertEquals("""
                {
                    "status": 404,
                    "method": "GET",
                    "errors": ["Product not found with id: ${product.id}"],
                    "path": "/products/${product.id}/price-stats"
                }
            """, body, JSONCompareMode.LENIENT)
    }

    def "404 - non-existing product id"() {
        given: "a non-existing product ID"
            Long nonExistingProductId = 9999L

        when: "requesting the price stats for the non-existing ID"
            def response = webActor.getPriceStats(nonExistingProductId)

        then: "the response is 404 with the product not found error"
            response.expectStatus().isNotFound()
            String body = response.expectBody(String).returnResult().getResponseBody()
            JSONAssert.assertEquals("""
                {
                    "status": 404,
                    "method": "GET",
                    "errors": ["Product not found with id: ${nonExistingProductId}"],
                    "path": "/products/${nonExistingProductId}/price-stats"
                }
            """, body, JSONCompareMode.LENIENT)
    }

    def "Full stats - min, max, average, odd median, 30d and 90d changes, days at lowest"() {
        given: "a product with 13 price history rows spanning 120 days"
            Product product = seedProduct("Full stats product", "full-stats-product")
            def rows = [
                    PriceHistoryRow.row(0, 549.99),
                    PriceHistoryRow.row(1, 529.99),
                    PriceHistoryRow.row(2, 509.99),
                    PriceHistoryRow.row(3, 499.99),
                    PriceHistoryRow.row(4, 499.99),
                    PriceHistoryRow.row(5, 499.99),
                    PriceHistoryRow.row(6, 519.99),
                    PriceHistoryRow.row(7, 539.99),
                    PriceHistoryRow.row(8, 559.99),
                    PriceHistoryRow.row(9, 579.99),
                    PriceHistoryRow.row(10, 599.99),
                    PriceHistoryRow.row(45, 619.99),
                    PriceHistoryRow.row(120, 649.99)
            ]
            seedPriceHistory(product, rows)

        when: "requesting the price stats"
            def response = webActor.getPriceStats(product.id)

        then: "the response is 200 with all statistics computed over the full history"
            response.expectStatus().isOk()
            String body = response.expectBody(String).returnResult().getResponseBody()
            JSONAssert.assertEquals("""
                {
                    "currentPrice": 549.99,
                    "lowestPrice": 499.99,
                    "highestPrice": 649.99,
                    "averagePrice": 550.76,
                    "medianPrice": 539.99,
                    "priceChange30d": -8.33,
                    "priceChange90d": -11.29,
                    "daysAtLowestPrice": 3
                }
            """, body, JSONCompareMode.LENIENT)
    }

    def "Single price point - all stats equal to that price and zero changes"() {
        given: "a product with a single price history row"
            Product product = seedProduct("Single point product", "single-point-product")
            seedPriceHistory(product, [PriceHistoryRow.row(0, 100.00)])

        when: "requesting the price stats"
            def response = webActor.getPriceStats(product.id)

        then: "the response is 200 with every stat equal to the only price"
            response.expectStatus().isOk()
            String body = response.expectBody(String).returnResult().getResponseBody()
            JSONAssert.assertEquals("""
                {
                    "currentPrice": 100.00,
                    "lowestPrice": 100.00,
                    "highestPrice": 100.00,
                    "averagePrice": 100.00,
                    "medianPrice": 100.00,
                    "priceChange30d": 0.00,
                    "priceChange90d": 0.00,
                    "daysAtLowestPrice": 1
                }
            """, body, JSONCompareMode.LENIENT)
    }

    def "Even number of points - median is the mean of the two middle prices"() {
        given: "a product with 4 price history rows"
            Product product = seedProduct("Even median product", "even-median-product")
            def rows = [
                    PriceHistoryRow.row(0, 100.00),
                    PriceHistoryRow.row(1, 50.00),
                    PriceHistoryRow.row(50, 75.00),
                    PriceHistoryRow.row(100, 25.00)
            ]
            seedPriceHistory(product, rows)

        when: "requesting the price stats"
            def response = webActor.getPriceStats(product.id)

        then: "the response is 200 with the median of the two middle prices"
            response.expectStatus().isOk()
            String body = response.expectBody(String).returnResult().getResponseBody()
            JSONAssert.assertEquals("""
                {
                    "currentPrice": 100.00,
                    "lowestPrice": 25.00,
                    "highestPrice": 100.00,
                    "averagePrice": 62.50,
                    "medianPrice": 62.50,
                    "priceChange30d": 100.00,
                    "priceChange90d": 33.33,
                    "daysAtLowestPrice": 1
                }
            """, body, JSONCompareMode.LENIENT)
    }

    def "Multiple days at the lowest price - counted even when the current price is not the lowest"() {
        given: "a product whose lowest price occurred on 3 past days"
            Product product = seedProduct("Past low product", "past-low-product")
            def rows = [
                    PriceHistoryRow.row(0, 200.00),
                    PriceHistoryRow.row(1, 100.00),
                    PriceHistoryRow.row(2, 100.00),
                    PriceHistoryRow.row(3, 100.00),
                    PriceHistoryRow.row(4, 150.00),
                    PriceHistoryRow.row(40, 120.00)
            ]
            seedPriceHistory(product, rows)

        when: "requesting the price stats"
            def response = webActor.getPriceStats(product.id)

        then: "the response is 200 with daysAtLowestPrice equal to the days priced at the minimum"
            response.expectStatus().isOk()
            String body = response.expectBody(String).returnResult().getResponseBody()
            JSONAssert.assertEquals("""
                {
                    "currentPrice": 200.00,
                    "lowestPrice": 100.00,
                    "highestPrice": 200.00,
                    "averagePrice": 128.33,
                    "medianPrice": 110.00,
                    "priceChange30d": 33.33,
                    "priceChange90d": 66.67,
                    "daysAtLowestPrice": 3
                }
            """, body, JSONCompareMode.LENIENT)
    }
}
