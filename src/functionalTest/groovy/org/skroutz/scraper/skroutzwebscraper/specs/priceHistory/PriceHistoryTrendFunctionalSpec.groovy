package org.skroutz.scraper.skroutzwebscraper.specs.priceHistory

import org.skroutz.scraper.skroutzwebscraper.product.domain.entity.Product
import org.skroutz.scraper.skroutzwebscraper.utils.base.BaseFunctionalSpec
import org.skyscreamer.jsonassert.JSONAssert
import org.skyscreamer.jsonassert.JSONCompareMode

class PriceHistoryTrendFunctionalSpec extends BaseFunctionalSpec {

    def "404 - product has no price history"() {
        given: "a saved product with no price history rows"
            Product product = seedProduct("No history product", "no-history-product")

        when: "requesting the price trend"
            def response = webActor.getPriceTrend(product.id)

        then: "the response is 404 with the product not found error"
            response.expectStatus().isNotFound()
            String body = response.expectBody(String).returnResult().getResponseBody()
            JSONAssert.assertEquals("""
                {
                    "status": 404,
                    "method": "GET",
                    "errors": ["Product not found with id: ${product.id}"],
                    "path": "/products/${product.id}/price-trend"
                }
            """, body, JSONCompareMode.LENIENT)
    }

    def "404 - non-existing product id"() {
        given: "a non-existing product ID"
            Long nonExistingProductId = 9999L

        when: "requesting the price trend for the non-existing ID"
            def response = webActor.getPriceTrend(nonExistingProductId)

        then: "the response is 404 with the product not found error"
            response.expectStatus().isNotFound()
            String body = response.expectBody(String).returnResult().getResponseBody()
            JSONAssert.assertEquals("""
                {
                    "status": 404,
                    "method": "GET",
                    "errors": ["Product not found with id: ${nonExistingProductId}"],
                    "path": "/products/${nonExistingProductId}/price-trend"
                }
            """, body, JSONCompareMode.LENIENT)
    }

    def "Boundary - 7 points is still insufficient data"() {
        given: "a product with 7 price history rows"
            Product product = seedProduct("Seven points product", "seven-points-product")
            seedPriceHistory(product, rangeRows(6, 0, 30.00))

        when: "requesting the price trend"
            def response = webActor.getPriceTrend(product.id)

        then: "the response is 200 with INSUFFICIENT_DATA and null metrics"
            response.expectStatus().isOk()
            String body = response.expectBody(String).returnResult().getResponseBody()
            JSONAssert.assertEquals("""
                {
                    "trend": "INSUFFICIENT_DATA",
                    "recentAverage": null,
                    "previousAverage": null,
                    "percentageChange": null,
                    "daysAnalyzed": 7
                }
            """, body, JSONCompareMode.LENIENT)
    }

    def "Boundary - exactly 8 points, both windows populated, stable"() {
        given: "a product with 8 price history rows, 4 per window"
            Product product = seedProduct("Stable product", "stable-product")
            def rows = [25, 19, 10, 7].collect { [daysAgo: it, price: 30.00] } +
                    [6, 3, 1, 0].collect { [daysAgo: it, price: 30.00] }
            seedPriceHistory(product, rows)

        when: "requesting the price trend"
            def response = webActor.getPriceTrend(product.id)

        then: "the response is 200 with a STABLE trend"
            response.expectStatus().isOk()
            String body = response.expectBody(String).returnResult().getResponseBody()
            JSONAssert.assertEquals("""
                {
                    "trend": "STABLE",
                    "recentAverage": 30.00,
                    "previousAverage": 30.00,
                    "percentageChange": 0.00,
                    "daysAnalyzed": 8
                }
            """, body, JSONCompareMode.LENIENT)
    }

    def "INCREASING trend"() {
        given: "a product with a recent average above the previous one"
            Product product = seedProduct("Increasing product", "increasing-product")
            def rows = [25, 19, 10, 7].collect { [daysAgo: it, price: 30.00] } +
                    [6, 3, 1, 0].collect { [daysAgo: it, price: 32.00] }
            seedPriceHistory(product, rows)

        when: "requesting the price trend"
            def response = webActor.getPriceTrend(product.id)

        then: "the response is 200 with an INCREASING trend"
            response.expectStatus().isOk()
            String body = response.expectBody(String).returnResult().getResponseBody()
            JSONAssert.assertEquals("""
                {
                    "trend": "INCREASING",
                    "recentAverage": 32.00,
                    "previousAverage": 30.00,
                    "percentageChange": 6.67,
                    "daysAnalyzed": 8
                }
            """, body, JSONCompareMode.LENIENT)
    }

    def "DECREASING trend"() {
        given: "a product with a recent average below the previous one"
            Product product = seedProduct("Decreasing product", "decreasing-product")
            def rows = [25, 19, 10, 7].collect { [daysAgo: it, price: 32.00] } +
                    [6, 3, 1, 0].collect { [daysAgo: it, price: 30.00] }
            seedPriceHistory(product, rows)

        when: "requesting the price trend"
            def response = webActor.getPriceTrend(product.id)

        then: "the response is 200 with a DECREASING trend"
            response.expectStatus().isOk()
            String body = response.expectBody(String).returnResult().getResponseBody()
            JSONAssert.assertEquals("""
                {
                    "trend": "DECREASING",
                    "recentAverage": 30.00,
                    "previousAverage": 32.00,
                    "percentageChange": -6.25,
                    "daysAnalyzed": 8
                }
            """, body, JSONCompareMode.LENIENT)
    }

    def "Boundary - exactly +2.00% change is still STABLE"() {
        given: "a product whose recent average is exactly 2% above the previous one"
            Product product = seedProduct("Boundary product", "boundary-product")
            def rows = [25, 19, 10, 7].collect { [daysAgo: it, price: 30.00] } +
                    [6, 3, 1, 0].collect { [daysAgo: it, price: 30.60] }
            seedPriceHistory(product, rows)

        when: "requesting the price trend"
            def response = webActor.getPriceTrend(product.id)

        then: "the response is 200 with a STABLE trend"
            response.expectStatus().isOk()
            String body = response.expectBody(String).returnResult().getResponseBody()
            JSONAssert.assertEquals("""
                {
                    "trend": "STABLE",
                    "recentAverage": 30.60,
                    "previousAverage": 30.00,
                    "percentageChange": 2.00,
                    "daysAnalyzed": 8
                }
            """, body, JSONCompareMode.LENIENT)
    }

    def "Previous window empty - all 8 points inside the last 7 days"() {
        given: "a product with 8 price history rows all inside the recent window"
            Product product = seedProduct("Recent only product", "recent-only-product")
            def rows = [-2, -2, -1, -1, 0, 0, -1, -2].collect { [daysAgo: it, price: 30.00] }
            seedPriceHistory(product, rows)

        when: "requesting the price trend"
            def response = webActor.getPriceTrend(product.id)

        then: "the response is 200 with INSUFFICIENT_DATA and null metrics"
            response.expectStatus().isOk()
            String body = response.expectBody(String).returnResult().getResponseBody()
            JSONAssert.assertEquals("""
                {
                    "trend": "INSUFFICIENT_DATA",
                    "recentAverage": null,
                    "previousAverage": null,
                    "percentageChange": null,
                    "daysAnalyzed": 8
                }
            """, body, JSONCompareMode.LENIENT)
    }
}
