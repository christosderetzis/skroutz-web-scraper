package org.skroutz.scraper.skroutzwebscraper.specs.priceHistory

import org.skroutz.scraper.skroutzwebscraper.product.domain.entity.Product
import org.skroutz.scraper.skroutzwebscraper.utils.base.BaseFunctionalSpec
import org.skyscreamer.jsonassert.JSONAssert
import org.skyscreamer.jsonassert.JSONCompareMode

class BuyRecommendationFunctionalSpec extends BaseFunctionalSpec {

    def "404 - product has no price history"() {
        given: "a saved product with no price history rows"
            Product product = seedProduct("No history product", "no-history-product")

        when: "requesting the buy recommendation"
            def response = webActor.getBuyRecommendation(product.id)

        then: "the response is 404 with the product not found error"
            response.expectStatus().isNotFound()
            String body = response.expectBody(String).returnResult().getResponseBody()
            JSONAssert.assertEquals("""
                {
                    "status": 404,
                    "method": "GET",
                    "errors": ["Product not found with id: ${product.id}"],
                    "path": "/products/${product.id}/buy-recommendation"
                }
            """, body, JSONCompareMode.LENIENT)
    }

    def "Boundary - fewer than 30 observations - INSUFFICIENT_DATA"() {
        given: "a product with 5 price history rows"
            Product product = seedProduct("Few observations product", "few-observations-product")
            seedPriceHistory(product, rangeRows(4, 0, 100.00))

        when: "requesting the buy recommendation"
            def response = webActor.getBuyRecommendation(product.id)

        then: "the response is 200 with INSUFFICIENT_DATA and null metrics"
            response.expectStatus().isOk()
            String body = response.expectBody(String).returnResult().getResponseBody()
            JSONAssert.assertEquals("""
                {
                    "recommendation": "INSUFFICIENT_DATA",
                    "score": 0,
                    "currentPrice": 100.00,
                    "historicalAverage": null,
                    "historicalMinimum": null,
                    "percentile": null,
                    "percentageBelowAverage": null,
                    "percentageAboveMinimum": null,
                    "trend": "INSUFFICIENT_DATA",
                    "validObservations": 5
                }
            """, body, JSONCompareMode.LENIENT)
    }

    def "Boundary - exactly 30 valid observations - BUY_NOW"() {
        given: "a product with 30 price history rows and a discounted latest price"
            Product product = seedProduct("Buy now product", "buy-now-product")
            def rows = rangeRows(29, 1, 100.00) + [[daysAgo: 0, price: 80.00]]
            seedPriceHistory(product, rows)

        when: "requesting the buy recommendation"
            def response = webActor.getBuyRecommendation(product.id)

        then: "the response is 200 with a BUY_NOW recommendation and a score of 100"
            response.expectStatus().isOk()
            String body = response.expectBody(String).returnResult().getResponseBody()
            JSONAssert.assertEquals("""
                {
                    "recommendation": "BUY_NOW",
                    "score": 100,
                    "currentPrice": 80.00,
                    "historicalAverage": 99.33,
                    "historicalMinimum": 80.00,
                    "percentile": 3.33,
                    "percentageBelowAverage": 19.46,
                    "percentageAboveMinimum": 0.00,
                    "trend": "DECREASING",
                    "validObservations": 30
                }
            """, body, JSONCompareMode.LENIENT)
    }

    def "GOOD_TIME_TO_BUY"() {
        given: "a product with a slightly discounted latest price"
            Product product = seedProduct("Good time product", "good-time-product")
            def rows = rangeRows(29, 1, 100.00) + [[daysAgo: 0, price: 95.00]]
            seedPriceHistory(product, rows)

        when: "requesting the buy recommendation"
            def response = webActor.getBuyRecommendation(product.id)

        then: "the response is 200 with a GOOD_TIME_TO_BUY recommendation and a score of 78"
            response.expectStatus().isOk()
            String body = response.expectBody(String).returnResult().getResponseBody()
            JSONAssert.assertEquals("""
                {
                    "recommendation": "GOOD_TIME_TO_BUY",
                    "score": 78,
                    "currentPrice": 95.00,
                    "historicalAverage": 99.83,
                    "historicalMinimum": 95.00,
                    "percentile": 3.33,
                    "percentageBelowAverage": 4.84,
                    "percentageAboveMinimum": 0.00,
                    "trend": "STABLE",
                    "validObservations": 30
                }
            """, body, JSONCompareMode.LENIENT)
    }

    def "NEUTRAL - boundary score 45"() {
        given: "a product with a mixed price history and a neutral latest price"
            Product product = seedProduct("Neutral product", "neutral-product")
            def rows = rangeRows(29, 23, 90.00) + rangeRows(22, 1, 110.00) + [[daysAgo: 0, price: 100.00]]
            seedPriceHistory(product, rows)

        when: "requesting the buy recommendation"
            def response = webActor.getBuyRecommendation(product.id)

        then: "the response is 200 with a NEUTRAL recommendation and a score of 45"
            response.expectStatus().isOk()
            String body = response.expectBody(String).returnResult().getResponseBody()
            JSONAssert.assertEquals("""
                {
                    "recommendation": "NEUTRAL",
                    "score": 45,
                    "currentPrice": 100.00,
                    "historicalAverage": 105.00,
                    "historicalMinimum": 90.00,
                    "percentile": 26.67,
                    "percentageBelowAverage": 4.76,
                    "percentageAboveMinimum": 11.11,
                    "trend": "INCREASING",
                    "validObservations": 30
                }
            """, body, JSONCompareMode.LENIENT)
    }

    def "WAIT"() {
        given: "a product with a high percentile latest price"
            Product product = seedProduct("Wait product", "wait-product")
            def rows = rangeRows(29, 7, 100.00) + rangeRows(6, 1, 105.00) + [[daysAgo: 0, price: 100.00]]
            seedPriceHistory(product, rows)

        when: "requesting the buy recommendation"
            def response = webActor.getBuyRecommendation(product.id)

        then: "the response is 200 with a WAIT recommendation and a score of 30"
            response.expectStatus().isOk()
            String body = response.expectBody(String).returnResult().getResponseBody()
            JSONAssert.assertEquals("""
                {
                    "recommendation": "WAIT",
                    "score": 30,
                    "currentPrice": 100.00,
                    "historicalAverage": 101.00,
                    "historicalMinimum": 100.00,
                    "percentile": 80.00,
                    "percentageBelowAverage": 0.99,
                    "percentageAboveMinimum": 0.00,
                    "trend": "INCREASING",
                    "validObservations": 30
                }
            """, body, JSONCompareMode.LENIENT)
    }

    def "STRONGLY_WAIT"() {
        given: "a product with a latest price far above the historical range"
            Product product = seedProduct("Strongly wait product", "strongly-wait-product")
            def rows = rangeRows(29, 1, 100.00) + [[daysAgo: 0, price: 150.00]]
            seedPriceHistory(product, rows)

        when: "requesting the buy recommendation"
            def response = webActor.getBuyRecommendation(product.id)

        then: "the response is 200 with a STRONGLY_WAIT recommendation and a score of 0"
            response.expectStatus().isOk()
            String body = response.expectBody(String).returnResult().getResponseBody()
            JSONAssert.assertEquals("""
                {
                    "recommendation": "STRONGLY_WAIT",
                    "score": 0,
                    "currentPrice": 150.00,
                    "historicalAverage": 101.67,
                    "historicalMinimum": 100.00,
                    "percentile": 100.00,
                    "percentageBelowAverage": -47.54,
                    "percentageAboveMinimum": 50.00,
                    "trend": "INCREASING",
                    "validObservations": 30
                }
            """, body, JSONCompareMode.LENIENT)
    }

    def "Edge - 35 rows but 0 valid observations - INSUFFICIENT_DATA"() {
        given: "a product with 35 price history rows all priced at zero"
            Product product = seedProduct("Zero price product", "zero-price-product")
            seedPriceHistory(product, rangeRows(34, 0, 0.00))

        when: "requesting the buy recommendation"
            def response = webActor.getBuyRecommendation(product.id)

        then: "the response is 200 with INSUFFICIENT_DATA and the latest row price as currentPrice"
            response.expectStatus().isOk()
            String body = response.expectBody(String).returnResult().getResponseBody()
            JSONAssert.assertEquals("""
                {
                    "recommendation": "INSUFFICIENT_DATA",
                    "score": 0,
                    "currentPrice": 0.00,
                    "historicalAverage": null,
                    "historicalMinimum": null,
                    "percentile": null,
                    "percentageBelowAverage": null,
                    "percentageAboveMinimum": null,
                    "trend": "INSUFFICIENT_DATA",
                    "validObservations": 0
                }
            """, body, JSONCompareMode.LENIENT)
    }
}
