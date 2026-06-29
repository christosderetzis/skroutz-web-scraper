package org.skroutz.scraper.skroutzwebscraper.specs

import org.skroutz.scraper.skroutzwebscraper.search.domain.entity.ProductDocument
import org.skroutz.scraper.skroutzwebscraper.utils.base.BaseFunctionalSpec
import org.skyscreamer.jsonassert.JSONAssert
import org.skyscreamer.jsonassert.JSONCompareMode

class SimilarProductsFunctionalSpec extends BaseFunctionalSpec {

    def "Happy path - returns similar products for a given product ID"() {
        given: "three laptops indexed in Elasticsearch sharing category and description"
            def laptop1 = createAndIndexProduct("MacBook Pro 16-inch", "laptops", 1999.99.toBigDecimal())
            def laptop2 = createAndIndexProduct("Dell XPS 15", "laptops", 1499.99.toBigDecimal())
            def laptop3 = createAndIndexProduct("Lenovo ThinkPad", "laptops", 1299.99.toBigDecimal())

        when: "requesting similar products for laptop1"
            def response = webActor.findSimilar(laptop1.id)

        then: "response is 200 OK"
            response.expectStatus().isOk()

        and: "response contains source product ID and similar products excluding the source"
            def body = response.expectBody(String).returnResult().getResponseBody()
            def expectedBody = """
                {
                    "sourceProductId": ${laptop1.id},
                    "products": [
                        {
                            "id": ${laptop2.id},
                            "title": "Dell XPS 15",
                            "category": "laptops",
                            "price": 1499.99
                        },
                        {
                            "id": ${laptop3.id},
                            "title": "Lenovo ThinkPad",
                            "category": "laptops",
                            "price": 1299.99
                        }
                    ],
                    "totalElements": 2
                }
                """
            JSONAssert.assertEquals(expectedBody, body, JSONCompareMode.LENIENT)
    }

    def "Happy path - similar products excludes the source product from results"() {
        given: "two products in the same category"
            def product1 = createAndIndexProduct("MacBook Pro 16-inch", "laptops", 1999.99.toBigDecimal())
            createAndIndexProduct("Dell XPS 15", "laptops", 1499.99.toBigDecimal())

        when: "requesting similar products for product1"
            def response = webActor.findSimilar(product1.id)

        then: "response is 200 OK"
            response.expectStatus().isOk()

        and: "source product is not included in results"
            def body = response.expectBody(String).returnResult().getResponseBody()
            !body.contains("\"id\": ${product1.id}")
    }

    def "Happy path - respects limit parameter"() {
        given: "four laptops indexed in Elasticsearch"
            def laptop1 = createAndIndexProduct("MacBook Pro 16-inch", "laptops", 1999.99.toBigDecimal())
            createAndIndexProduct("Dell XPS 15", "laptops", 1499.99.toBigDecimal())
            createAndIndexProduct("Lenovo ThinkPad", "laptops", 1299.99.toBigDecimal())
            createAndIndexProduct("HP Pavilion", "laptops", 899.99.toBigDecimal())

        when: "requesting similar products with limit of 2"
            def response = webActor.findSimilar(laptop1.id, 2)

        then: "response is 200 OK"
            response.expectStatus().isOk()

        and: "at most 2 products are returned"
            def body = response.expectBody(String).returnResult().getResponseBody()
            def productsCount = objectMapper.readTree(body).get("products").size()
            assert productsCount == 2
    }

    def "Happy path - uses default limit of 10"() {
        given: "12 laptops indexed in Elasticsearch"
            def products = (1..12).collect { createAndIndexProduct("Laptop ${it}", "laptops", (500 + it * 100).toBigDecimal()) }

        when: "requesting similar products without specifying limit"
            def response = webActor.findSimilar(products[0].id)

        then: "response is 200 OK"
            response.expectStatus().isOk()

        and: "at most 10 products are returned (default limit)"
            def body = response.expectBody(String).returnResult().getResponseBody()
            def productsCount = objectMapper.readTree(body).get("products").size()
            assert productsCount == 10
    }

    def "Happy path - returns empty results when product does not exist"() {
        given: "a product indexed in Elasticsearch"
            createAndIndexProduct("MacBook Pro 16-inch", "laptops", 1999.99.toBigDecimal())

        when: "requesting similar products for a non-existent ID"
            def response = webActor.findSimilar(99999L)

        then: "response is 200 OK"
            response.expectStatus().isOk()

        and: "response contains empty products list and zero totalElements"
            def body = response.expectBody(String).returnResult().getResponseBody()
            def expectedBody = """
                {
                    "sourceProductId": 99999,
                    "products": [],
                    "totalElements": 0
                }
                """
            JSONAssert.assertEquals(expectedBody, body, JSONCompareMode.LENIENT)
    }

    def "Happy path - returns empty results when only one product exists in category"() {
        given: "a single product indexed in Elasticsearch"
            def product = createAndIndexProduct("MacBook Pro 16-inch", "laptops", 1999.99.toBigDecimal())

        when: "requesting similar products for that product"
            def response = webActor.findSimilar(product.id)

        then: "response is 200 OK"
            response.expectStatus().isOk()

        and: "response contains empty products list since minDocFreq requires at least 2 docs"
            def body = response.expectBody(String).returnResult().getResponseBody()
        def expectedBody = """
                {
                    "sourceProductId": ${product.id},
                    "products": [],
                    "totalElements": 0
                }
                """
        JSONAssert.assertEquals(expectedBody, body, JSONCompareMode.LENIENT)
    }

    def "Happy path - products in different categories are not returned as similar"() {
        given: "products indexed across two different categories"
            def laptop = createAndIndexProduct("MacBook Pro 16-inch", "laptops", 1999.99.toBigDecimal())
            createAndIndexProduct("iPhone 15 Pro", "phones", 999.99.toBigDecimal())

        when: "requesting similar products for the laptop"
            def response = webActor.findSimilar(laptop.id)

        then: "response is 200 OK"
            response.expectStatus().isOk()

        and: "no products from the phones category are returned"
            def body = response.expectBody(String).returnResult().getResponseBody()
            !body.contains('"category": "phones"')
    }

    def "Unhappy path - negative limit returns 400 Bad Request"() {
        when: "requesting similar products with limit = -1"
            def response = webActor.findSimilar(1, -1)

        then: "response is 400 Bad Request"
            response.expectStatus().isBadRequest()
    }

    def "Unhappy path - zero limit returns 400 Bad Request"() {
        when: "requesting similar products with limit = 0"
            def response = webActor.findSimilar(1, 0)

        then: "response is 400 Bad Request"
            response.expectStatus().isBadRequest()
    }

    def "Happy path - similar products indexed directly in Elasticsearch with shared terms"() {
        given: "products indexed directly in Elasticsearch with overlapping title terms"
        def doc1 = ProductDocument.builder()
                .id(30001L)
                .title("Gaming Laptop RGB")
                .category("laptops")
                .price(1500.00.toBigDecimal())
                .description("High performance gaming laptop with RGB keyboard")
                .build()
        def doc2 = ProductDocument.builder()
                .id(30002L)
                .title("Gaming Laptop Pro")
                .category("laptops")
                .price(2000.00.toBigDecimal())
                .description("Professional gaming laptop with RGB lighting")
                .build()
        def doc3 = ProductDocument.builder()
                .id(30003L)
                .title("Macbook Pro 16-inch M5")
                .category("laptops")
                .price(2500.00.toBigDecimal())
                .description("Premium Apple device running macOS with advanced silicon architecture")
                .build()
        def doc4 = ProductDocument.builder()
                .id(30004L)
                .title("Office Desktop PC")
                .category("desktops")
                .price(800.00.toBigDecimal())
                .description("Standard office desktop computer")
                .build()

        productElasticsearchRepository.saveAll([doc1, doc2, doc3, doc4])
        Thread.sleep(200)

        when: "requesting similar products for doc1"
        def response = webActor.findSimilar(30001L)

        then: "response is 200 OK"
        response.expectStatus().isOk()

        and: "returns gaming laptop with shared terms, not the office desktop"
        def body = response.expectBody(String).returnResult().getResponseBody()
        def products = objectMapper.readTree(body).get("products")
        def productIds = products.collect { it.get("id").longValue() } as Set
        assert productIds.contains(doc2.id)
        assert !productIds.contains(doc3.id)
        assert !productIds.contains(doc4.id)
    }
}
