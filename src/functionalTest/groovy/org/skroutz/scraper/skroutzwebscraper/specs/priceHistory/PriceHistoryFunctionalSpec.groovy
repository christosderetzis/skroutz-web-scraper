package org.skroutz.scraper.skroutzwebscraper.specs.priceHistory

import org.skroutz.scraper.skroutzwebscraper.product.domain.entity.Product
import org.skroutz.scraper.skroutzwebscraper.utils.base.BaseFunctionalSpec
import org.skroutz.scraper.skroutzwebscraper.utils.base.PriceHistoryRow
import org.skyscreamer.jsonassert.JSONAssert
import org.skyscreamer.jsonassert.JSONCompareMode

class PriceHistoryFunctionalSpec extends BaseFunctionalSpec {

    def "404 - product has no price history"() {
        given: "a saved product with no price history rows"
            Product product = seedProduct("No history product", "no-history-price-history-product")

        when: "requesting the price history"
            def response = webActor.getPriceHistory(product.id)

        then: "the response is 404 with the product not found error"
            response.expectStatus().isNotFound()
            String body = response.expectBody(String).returnResult().getResponseBody()
            JSONAssert.assertEquals("""
                {
                    "status": 404,
                    "method": "GET",
                    "errors": ["Product not found with id: ${product.id}"],
                    "path": "/products/${product.id}/price-history"
                }
            """, body, JSONCompareMode.LENIENT)
    }

    def "404 - non-existing product id"() {
        given: "a non-existing product ID"
            Long nonExistingProductId = 9999L

        when: "requesting the price history for the non-existing ID"
            def response = webActor.getPriceHistory(nonExistingProductId)

        then: "the response is 404 with the product not found error"
            response.expectStatus().isNotFound()
            String body = response.expectBody(String).returnResult().getResponseBody()
            JSONAssert.assertEquals("""
                {
                    "status": 404,
                    "method": "GET",
                    "errors": ["Product not found with id: ${nonExistingProductId}"],
                    "path": "/products/${nonExistingProductId}/price-history"
                }
            """, body, JSONCompareMode.LENIENT)
    }

    def "Full history - all points in ascending date order with product id and current price"() {
        given: "a product with 2 price history rows"
            Product product = seedProduct("Full history product", "full-history-product")
            def rows = [
                    PriceHistoryRow.row(31, 599.99),
                    PriceHistoryRow.row(17, 549.99)
            ]
            seedPriceHistory(product, rows)

        when: "requesting the price history"
            def response = webActor.getPriceHistory(product.id)

        then: "the response is 200 with every point ascending and the current price as the latest one"
            response.expectStatus().isOk()
            String body = response.expectBody(String).returnResult().getResponseBody()
            JSONAssert.assertEquals("""
                {
                    "productId": ${product.id},
                    "currentPrice": 549.99,
                    "history": [
                        {"date": "2026-08-01", "price": 599.99},
                        {"date": "2026-08-15", "price": 549.99}
                    ]
                }
            """, body, JSONCompareMode.LENIENT)
    }

    def "from filter - only points from the given date onward, inclusive"() {
        given: "a product with 2 price history rows"
            Product product = seedProduct("From filter product", "from-filter-product")
            def rows = [
                    PriceHistoryRow.row(31, 599.99),
                    PriceHistoryRow.row(17, 549.99)
            ]
            seedPriceHistory(product, rows)

        when: "requesting the price history with from on the second point date"
            def response = webActor.getPriceHistory(product.id, "2026-08-15", null)

        then: "the response is 200 with only the points on or after the from date"
            response.expectStatus().isOk()
            String body = response.expectBody(String).returnResult().getResponseBody()
            JSONAssert.assertEquals("""
                {
                    "productId": ${product.id},
                    "currentPrice": 549.99,
                    "history": [
                        {"date": "2026-08-15", "price": 549.99}
                    ]
                }
            """, body, JSONCompareMode.LENIENT)
    }

    def "to filter - only points up to the given date, inclusive"() {
        given: "a product with 2 price history rows"
            Product product = seedProduct("To filter product", "to-filter-product")
            def rows = [
                    PriceHistoryRow.row(31, 599.99),
                    PriceHistoryRow.row(17, 549.99)
            ]
            seedPriceHistory(product, rows)

        when: "requesting the price history with to on the first point date"
            def response = webActor.getPriceHistory(product.id, null, "2026-08-01")

        then: "the response is 200 with only the points on or before the to date"
            response.expectStatus().isOk()
            String body = response.expectBody(String).returnResult().getResponseBody()
            JSONAssert.assertEquals("""
                {
                    "productId": ${product.id},
                    "currentPrice": 549.99,
                    "history": [
                        {"date": "2026-08-01", "price": 599.99}
                    ]
                }
            """, body, JSONCompareMode.LENIENT)
    }

    def "from and to filters - inclusive range returns every point in between"() {
        given: "a product with 3 price history rows"
            Product product = seedProduct("Range filter product", "range-filter-product")
            def rows = [
                    PriceHistoryRow.row(31, 599.99),
                    PriceHistoryRow.row(17, 549.99),
                    PriceHistoryRow.row(1, 539.99)
            ]
            seedPriceHistory(product, rows)

        when: "requesting the price history with from and to covering the first two points"
            def response = webActor.getPriceHistory(product.id, "2026-08-01", "2026-08-15")

        then: "the response is 200 with the points inside the inclusive range"
            response.expectStatus().isOk()
            String body = response.expectBody(String).returnResult().getResponseBody()
            JSONAssert.assertEquals("""
                {
                    "productId": ${product.id},
                    "currentPrice": 539.99,
                    "history": [
                        {"date": "2026-08-01", "price": 599.99},
                        {"date": "2026-08-15", "price": 549.99}
                    ]
                }
            """, body, JSONCompareMode.LENIENT)
    }

    def "range excluding all points - empty history with the current price"() {
        given: "a product with a single price history row"
            Product product = seedProduct("Empty range product", "empty-range-product")
            seedPriceHistory(product, [PriceHistoryRow.row(17, 549.99)])

        when: "requesting the price history with a from date beyond the history"
            def response = webActor.getPriceHistory(product.id, "2027-01-01", null)

        then: "the response is 200 with an empty history and the current price intact"
            response.expectStatus().isOk()
            String body = response.expectBody(String).returnResult().getResponseBody()
            JSONAssert.assertEquals("""
                {
                    "productId": ${product.id},
                    "currentPrice": 549.99,
                    "history": []
                }
            """, body, JSONCompareMode.LENIENT)
    }
}
