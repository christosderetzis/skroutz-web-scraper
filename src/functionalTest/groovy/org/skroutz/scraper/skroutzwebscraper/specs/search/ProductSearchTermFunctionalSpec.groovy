package org.skroutz.scraper.skroutzwebscraper.specs.search

import org.skroutz.scraper.skroutzwebscraper.category.domain.schema.CategoryMappingSchema
import org.skroutz.scraper.skroutzwebscraper.category.domain.schema.DirectFieldMapping
import org.skroutz.scraper.skroutzwebscraper.category.domain.schema.FeatureExtraction
import org.skroutz.scraper.skroutzwebscraper.category.domain.schema.FeatureFieldMapping
import org.skroutz.scraper.skroutzwebscraper.category.domain.schema.FieldType
import org.skroutz.scraper.skroutzwebscraper.category.infrastructure.dto.CategorySchemaCreateRequestDto
import org.skroutz.scraper.skroutzwebscraper.search.infrastructure.dto.ProductSearchRequest
import org.skroutz.scraper.skroutzwebscraper.utils.base.BaseFunctionalSpec
import org.skyscreamer.jsonassert.JSONAssert
import org.skyscreamer.jsonassert.JSONCompareMode

class ProductSearchTermFunctionalSpec extends BaseFunctionalSpec {

    def setup() {
        def laptopsSchema = CategoryMappingSchema.builder()
                .directFields([
                        DirectFieldMapping.builder().path("span.operating_system").target("operating_system").type(null).build(),
                        DirectFieldMapping.builder().path("span.ram").target("ram").type(FieldType.NUMERIC).build(),
                        DirectFieldMapping.builder().path("span.display").target("display").type(FieldType.NUMERIC).build(),
                        DirectFieldMapping.builder().path("span.release_year").target("release_year").type(FieldType.INTEGER).build()
                ])
                .arrayFields([
                        FeatureFieldMapping.builder().path("Network & Connectivity.Network Connection").target("features").type(FeatureExtraction.VALUE).build()
                ])
                .build()
        webActor.createCategorySchema(new CategorySchemaCreateRequestDto("laptops", laptopsSchema))
                .expectStatus().isCreated()

        def phonesSchema = CategoryMappingSchema.builder()
                .directFields([])
                .arrayFields([])
                .build()
        webActor.createCategorySchema(new CategorySchemaCreateRequestDto("phones", phonesSchema))
                .expectStatus().isCreated()
    }

    def "Term-only search spans all categories and returns brand and category filters"() {
        given:
            def iphone = createAndIndexProduct("Apple iPhone 15 Pro", "phones", "Apple", 1200.00.toBigDecimal())
            createAndIndexProduct("Samsung Galaxy S24 Ultra", "phones", "Samsung", 1100.00.toBigDecimal())
            def macbook = createAndIndexProduct("Apple MacBook Pro 16", "laptops", "Apple", 2500.00.toBigDecimal())
            createAndIndexProduct("Lenovo ThinkPad X1 Carbon", "laptops", "Lenovo", 1800.00.toBigDecimal())

        when:
            def response = webActor.searchProducts(new ProductSearchRequest(searchTerm: "pro"))

        then:
            response.expectStatus().isOk()
            def body = response.expectBody(String).returnResult().getResponseBody()

        and: "only the two titles containing 'pro' are returned, spanning both categories"
            def productsJsonArray = [iphone, macbook].collect { productJson(it) }.join(",")
            def expectedBody = """
                {
                    "products": [ ${productsJsonArray} ],
                    "filters": {
                        "brand": [ { "value": "Apple", "count": 2 } ],
                        "category": [ { "value": "phones", "count": 1 }, { "value": "laptops", "count": 1 } ]
                    },
                    "totalElements": 2,
                    "totalPages": 1,
                    "page": 0,
                    "size": 20
                }
                """
            JSONAssert.assertEquals(expectedBody, body, JSONCompareMode.LENIENT)
    }

    def "Term search within a category keeps the spec filters"() {
        given:
            createAndIndexProduct("Apple iPhone 15 Pro", "phones", "Apple", 1200.00.toBigDecimal())
            def macbook = createAndIndexProduct("Apple MacBook Pro 16", "laptops", "Apple", 2500.00.toBigDecimal())

        when:
            def response = webActor.searchProducts(new ProductSearchRequest(searchTerm: "pro", category: "laptops"))

        then:
            response.expectStatus().isOk()
            def body = response.expectBody(String).returnResult().getResponseBody()

        and: "only the laptop match is returned, with brand plus the laptops spec filters (no category filter)"
            def productsJsonArray = [macbook].collect { productJson(it) }.join(",")
            def expectedBody = """
                {
                    "products": [ ${productsJsonArray} ],
                    "filters": {
                        "brand": [ { "value": "Apple", "count": 1 } ],
                        "operating_system": [],
                        "ram": [],
                        "display": [],
                        "release_year": [],
                        "features": []
                    },
                    "totalElements": 1,
                    "totalPages": 1,
                    "page": 0,
                    "size": 20
                }
                """
            JSONAssert.assertEquals(expectedBody, body, JSONCompareMode.LENIENT)
    }

    def "Term-only search matching a single category returns that category's spec filters"() {
        given:
            def macbook = createAndIndexProduct("Apple MacBook Pro 16", "laptops", "Apple", 2500.00.toBigDecimal())
            def thinkpad = createAndIndexProduct("Lenovo ThinkPad Pro X1", "laptops", "Lenovo", 1800.00.toBigDecimal())
            createAndIndexProduct("Sony Bravia TV 9", "phones", "Sony", 300.00.toBigDecimal())

        when:
            def response = webActor.searchProducts(new ProductSearchRequest(searchTerm: "pro"))

        then:
            response.expectStatus().isOk()
            def body = response.expectBody(String).returnResult().getResponseBody()

        and: "both laptop matches are returned, with brand plus the laptops spec filters (no category filter)"
            def productsJsonArray = [macbook, thinkpad].collect { productJson(it) }.join(",")
            def expectedBody = """
                {
                    "products": [ ${productsJsonArray} ],
                    "filters": {
                        "brand": [ { "value": "Apple", "count": 1 }, { "value": "Lenovo", "count": 1 } ],
                        "operating_system": [],
                        "ram": [],
                        "display": [],
                        "release_year": [],
                        "features": []
                    },
                    "totalElements": 2,
                    "totalPages": 1,
                    "page": 0,
                    "size": 20
                }
                """
            JSONAssert.assertEquals(expectedBody, body, JSONCompareMode.LENIENT)
    }

    def "Term-only search with no matches returns brand and category filters"() {
        given:
            createAndIndexProduct("Apple MacBook Air 13", "laptops", "Apple", 2000.00.toBigDecimal())

        when:
            def response = webActor.searchProducts(new ProductSearchRequest(searchTerm: "nonexistentterm"))

        then:
            response.expectStatus().isOk()
            def body = response.expectBody(String).returnResult().getResponseBody()

        and: "no products match, but the cross-category brand and category filters are still present"
            def expectedBody = """
                {
                    "products": [],
                    "filters": {
                        "brand": [],
                        "category": []
                    },
                    "totalElements": 0,
                    "totalPages": 0,
                    "page": 0,
                    "size": 20
                }
                """
            JSONAssert.assertEquals(expectedBody, body, JSONCompareMode.LENIENT)
    }

    def "Cross-category term search pagination"() {
        given:
            def iphone = createAndIndexProduct("Apple iPhone 15 Pro", "phones", "Apple", 1200.00.toBigDecimal())
            def macbook = createAndIndexProduct("Apple MacBook Pro 16", "laptops", "Apple", 2500.00.toBigDecimal())

        when:
            def firstPage = webActor.searchProducts(new ProductSearchRequest(searchTerm: "pro", size: 1))
            def secondPage = webActor.searchProducts(new ProductSearchRequest(searchTerm: "pro", page: 1, size: 1))

        then:
            firstPage.expectStatus().isOk()
            def firstBody = firstPage.expectBody(String).returnResult().getResponseBody()
            def firstProduct = [iphone].collect { productJson(it) }.join(",")
            JSONAssert.assertEquals("""
                {
                    "products": [ ${firstProduct} ],
                    "filters": {
                        "brand": [ { "value": "Apple", "count": 2 } ],
                        "category": [ { "value": "phones", "count": 1 }, { "value": "laptops", "count": 1 } ]
                    },
                    "totalElements": 2,
                    "totalPages": 2,
                    "page": 0,
                    "size": 1
                }
                """, firstBody, JSONCompareMode.LENIENT)

        and: "second page returns the same global totals"
            secondPage.expectStatus().isOk()
            def secondBody = secondPage.expectBody(String).returnResult().getResponseBody()
            def secondProduct = [macbook].collect { productJson(it) }.join(",")
            JSONAssert.assertEquals("""
                {
                    "products": [ ${secondProduct} ],
                    "filters": {
                        "brand": [ { "value": "Apple", "count": 2 } ],
                        "category": [ { "value": "phones", "count": 1 }, { "value": "laptops", "count": 1 } ]
                    },
                    "totalElements": 2,
                    "totalPages": 2,
                    "page": 1,
                    "size": 1
                }
                """, secondBody, JSONCompareMode.LENIENT)
    }

    def "Search without category or searchTerm returns 400"() {
        when:
            def response = webActor.searchProducts(new ProductSearchRequest())

        then:
            response.expectStatus().isBadRequest()
    }

    def "Search with blank category and blank searchTerm returns 400"() {
        when:
            def response = webActor.searchProducts(new ProductSearchRequest(category: "   ", searchTerm: ""))

        then:
            response.expectStatus().isBadRequest()
    }

    private String productJson(p) {
        """
        {
            "id": ${p.id},
            "url": "${p.url}",
            "title": "${p.title}",
            "category": "${p.category}",
            "brand": "${p.brand}",
            "price": ${p.price},
            "imageUrl": "${p.imageUrl}",
            "description": "${p.description}",
            "rating": ${p.rating}
        }
        """
    }
}
