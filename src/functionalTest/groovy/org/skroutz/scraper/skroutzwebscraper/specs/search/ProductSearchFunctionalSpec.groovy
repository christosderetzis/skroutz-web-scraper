package org.skroutz.scraper.skroutzwebscraper.specs.search

import org.skroutz.scraper.skroutzwebscraper.search.domain.entity.ProductDocument
import org.skroutz.scraper.skroutzwebscraper.category.infrastructure.dto.CategorySchemaCreateRequestDto
import org.skroutz.scraper.skroutzwebscraper.search.infrastructure.dto.FilterRequest
import org.skroutz.scraper.skroutzwebscraper.search.infrastructure.dto.FilterType
import org.skroutz.scraper.skroutzwebscraper.search.infrastructure.dto.ProductSearchRequest
import org.skroutz.scraper.skroutzwebscraper.category.domain.schema.CategoryMappingSchema
import org.skroutz.scraper.skroutzwebscraper.category.domain.schema.DirectFieldMapping
import org.skroutz.scraper.skroutzwebscraper.category.domain.schema.FeatureExtraction
import org.skroutz.scraper.skroutzwebscraper.category.domain.schema.FeatureFieldMapping
import org.skroutz.scraper.skroutzwebscraper.category.domain.schema.FieldType
import org.skroutz.scraper.skroutzwebscraper.utils.base.BaseFunctionalSpec
import org.skyscreamer.jsonassert.JSONAssert
import org.skyscreamer.jsonassert.JSONCompareMode
import spock.lang.Unroll

class ProductSearchFunctionalSpec extends BaseFunctionalSpec {

    // BaseFunctionalSpec.setup() wipes all schemas first; this runs after it.
    def setup() {
        def laptopsSchema = CategoryMappingSchema.builder()
                .directFields([
                    DirectFieldMapping.builder().path("span.brand").target("brand").type(FieldType.STRING).build(),
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

    def "Happy path - returns only products matching the requested category"() {
        given: "products indexed across two categories"
            def laptop1 = createAndIndexProduct("MacBook Pro 16-inch", "laptops", "Apple", 1999.99.toBigDecimal())
            def laptop2 = createAndIndexProduct("Dell XPS 15", "laptops", "Dell", 1499.99.toBigDecimal())
            createAndIndexProduct("iPhone 15 Pro", "phones", "Apple", 999.99.toBigDecimal())

        when: "searching for the laptops category"
            def request = new ProductSearchRequest(category: "laptops")
            def response = webActor.searchProducts(request)

        then: "response is 200 OK"
            response.expectStatus().isOk()

        and: "only laptops are returned with correct totals"
            def body = response.expectBody(String).returnResult().getResponseBody()
            def expectedBody = """
                {
                    "products": [
                        {
                            "id": ${laptop1.id},
                            "title": "MacBook Pro 16-inch",
                            "url": "http://example.com/macbook-pro-16-inch",
                            "category": "laptops",
                            "brand": "Apple",
                            "price": 1999.99,
                            "imageUrl": "http://example.com/image.jpg",
                            "description": "Test product",
                            "rating": 4.5 
                        },
                        {
                            "id": ${laptop2.id},
                            "title": "Dell XPS 15",
                            "url": "http://example.com/dell-xps-15",
                            "category": "laptops",
                            "brand": "Dell",
                            "price": 1499.99,
                            "imageUrl": "http://example.com/image.jpg",
                            "description": "Test product",
                            "rating": 4.5 
                        }
                    ],
                    "filters": {
                        "brand": [],
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
        JSONAssert.assertEquals(body, expectedBody, JSONCompareMode.LENIENT)
    }

    def "Happy path - returns empty results when no products exist in the requested category"() {
        given: "a product indexed under laptops"
            createAndIndexProduct("MacBook Pro 16-inch", "laptops", "Apple", 1999.99.toBigDecimal())

        when: "searching for the phones category"
            def request = new ProductSearchRequest(category: "phones")
            def response = webActor.searchProducts(request)

        then: "response is 200 OK with empty product list and zero totals"
            response.expectStatus().isOk()

        and: "response body contains empty products and correct metadata"
            def body = response.expectBody(String).returnResult().getResponseBody()
            def expectedBody = """
                {
                    "products": [],
                    "filters": {
                        "brand": [],
                        "operating_system": [],
                        "ram": [],
                        "display": [],
                        "release_year": [],
                        "features": []
                    },
                    "totalElements": 0,
                    "totalPages": 0,
                    "page": 0,
                    "size": 20
                }
                """
        JSONAssert.assertEquals(body, expectedBody, JSONCompareMode.LENIENT)
    }

    @Unroll
    def "Happy path - price filtering with #scenario narrows results correctly"() {
        given: "three laptops indexed at different price points"
            def productMap = [
                    budget : createAndIndexProduct("Budget Laptop", "laptops", "Apple", 500.00.toBigDecimal()),
                    mid    : createAndIndexProduct("Mid-Range Laptop", "laptops", "Apple", 1000.00.toBigDecimal()),
                    premium: createAndIndexProduct("Premium Laptop", "laptops", "Apple", 1500.00.toBigDecimal())
            ]

        when: "searching with specific price filters"
            def request = new ProductSearchRequest(category: "laptops", minPrice: min, maxPrice: max)
            def response = webActor.searchProducts(request)

        then: "response is 200 OK"
            response.expectStatus().isOk()

        and: "the JSON response matches the dynamically constructed expected JSON"
            def body = response.expectBody(String).returnResult().getResponseBody()

            // 1. Map the keys from the data table to the actual generated product objects
            def expectedProducts = expectedKeys.collect { key -> productMap[key] }

            // 2. Build the inner products JSON array dynamically
            def productsJsonArray = expectedProducts.collect { p ->
                """
                {
                    "id": ${p.id},
                    "title": "${p.title}",
                    "url": "${p.url}",
                    "category": "${p.category}",
                    "brand": "${p.brand}",
                    "price": ${p.price},
                    "imageUrl": "${p.imageUrl}",
                    "description": "${p.description}",
                    "rating": ${p.rating}
                }
                """
            }.join(",") // Joins multiple products with a comma

            // 3. Assemble the full expected payload
            def expectedBody = """
                {
                    "products": [ ${productsJsonArray} ],
                    "filters": {
                        "brand": [],
                        "operating_system": [],
                        "ram": [],
                        "display": [],
                        "release_year": [],
                        "features": []
                    },
                    "totalElements": ${expectedProducts.size()},
                    "totalPages": 1,
                    "page": 0,
                    "size": 20
                }
                """

            // Note: JSONAssert expects (expected JSON, actual JSON, mode)
            JSONAssert.assertEquals(expectedBody, body, JSONCompareMode.LENIENT)

        where:
            scenario               | min   | max    | expectedKeys
            "minPrice threshold"   | 800.0 | null   | ["mid", "premium"]
            "maxPrice threshold"   | null  | 1200.0 | ["budget", "mid"]
            "combined price range" | 800.0 | 1200.0 | ["mid"]
    }

    @Unroll
    def "Happy path - pagination for page #page with size #size returns correct subset and metadata"() {
        given: "five laptops indexed sequentially"
        def indexedProducts = (1..5).collect {
            createAndIndexProduct("Laptop ${it}", "laptops", "BrandX", (500 + it * 100).toBigDecimal())
        }

        when: "requesting a specific page and size combination"
        def request = new ProductSearchRequest(category: "laptops", page: page, size: size)
        def response = webActor.searchProducts(request)

        then: "response is 200 OK"
        response.expectStatus().isOk()

        and: "the full JSON structure matches pagination boundaries and global aggregations"
        def body = response.expectBody(String).returnResult().getResponseBody()

        // 1. Calculate which products should appear on the current page slice
        int fromIndex = page * size
        int toIndex = Math.min(fromIndex + size, indexedProducts.size())
        def expectedPageProducts = (fromIndex < indexedProducts.size()) ? indexedProducts.subList(fromIndex, toIndex) : []

        // 2. Dynamically build the inner expected products array
        def productsJsonArray = expectedPageProducts.collect { p ->
            """
            {
                "id": ${p.id},
                "title": "${p.title}",
                "category": "${p.category}",
                "brand": "${p.brand}",
                "price": ${p.price}
            }
            """
        }.join(",")

        // 3. Assemble the full expected layout
        def expectedBody = """
            {
                "products": [ ${productsJsonArray} ],
                "filters": {
                    "brand": [],
                    "operating_system": [],
                    "ram": [],
                    "display": [],
                    "release_year": [],
                    "features": []
                },
                "totalElements": 5,
                "totalPages": ${expectedTotalPages},
                "page": ${page},
                "size": ${size}
            }
            """

        JSONAssert.assertEquals(expectedBody, body, JSONCompareMode.LENIENT)

        where:
        page | size | expectedTotalPages
        0    | 2    | 3                  // First page: gets Laptop 1 and Laptop 2
        1    | 2    | 3                  // Second page (non-overlapping): gets Laptop 3 and Laptop 4
        2    | 2    | 3                  // Last page: gets Laptop 5
    }

    def "Happy path - TERM filter returns only products matching the specification value"() {
        given: "two laptops with different brand specifications indexed directly in Elasticsearch"
            def appleDoc = ProductDocument.builder()
                    .id(10001L)
                    .title("MacBook Pro")
                    .category("laptops")
                    .brand("Apple")
                    .price(1999.99.toBigDecimal())
                    .specifications(["brand": "Apple"])
                    .build()
            def dellDoc = ProductDocument.builder()
                    .id(10002L)
                    .title("XPS 15")
                    .category("laptops")
                    .brand("Dell")
                    .price(1499.99.toBigDecimal())
                    .specifications(["brand": "Dell"])
                    .build()

            productElasticsearchRepository.saveAll([appleDoc, dellDoc])
            Thread.sleep(200)

        when: "searching for brand = Apple"
            def filter = new FilterRequest(key: "brand", type: FilterType.TERM, values: ["Apple"])
            def request = new ProductSearchRequest(category: "laptops", filters: [filter])
            def response = webActor.searchProducts(request)

        then: "response is 200 OK"
            response.expectStatus().isOk()

        and: "the JSON response precisely matches the expected structural layout"
            def body = response.expectBody(String).returnResult().getResponseBody()

            def expectedBody = """
                {
                    "products": [
                        {
                            "id": 10001,
                            "title": "MacBook Pro",
                            "category": "laptops",
                            "brand": "Apple",
                            "price": 1999.99
                        }
                    ],
                    "filters": {
                        "brand": [
                            {
                                "value": "Apple",
                                "count": 1
                            }
                        ],
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

    @Unroll
    def "Happy path - TERM filters handle advanced scenario: #scenario"() {
        given: "a diverse set of laptops indexed in Elasticsearch"
            def laptop1 = ProductDocument.builder()
                    .id(20001L).title("MacBook Pro").category("laptops").brand("Apple").price(2000.00)
                    .specifications(["brand": "Apple", "operating_system": "macOS", "ram": 16, "features": ["Touchscreen"]])
                    .build()
            def laptop2 = ProductDocument.builder()
                    .id(20002L).title("XPS 15").category("laptops").brand("Dell").price(1500.00)
                    .specifications(["brand": "Dell", "operating_system": "Windows", "ram": 8, "features": ["Backlit Keyboard"]])
                    .build()

            productElasticsearchRepository.saveAll([laptop1, laptop2])
            Thread.sleep(200)

        when: "executing the search request"
            def request = new ProductSearchRequest(category: "laptops", filters: targetFilters)
            def response = webActor.searchProducts(request)

        then: "response is 200 OK"
            response.expectStatus().isOk()

        and: "the JSON response matches expected elements"
            def body = response.expectBody(String).returnResult().getResponseBody()
            JSONAssert.assertEquals(expectedJson, body, JSONCompareMode.LENIENT)

        where:
            scenario | targetFilters | expectedJson

            "Multiple values (OR)" |
                    [new FilterRequest(key: "brand", type: FilterType.TERM, values: ["Apple", "Dell"])] |
                    '{"totalElements": 2, "products": [{"id": 20001}, {"id": 20002}]}'

            "Combined filters (AND) - No Match" |
                    [
                            new FilterRequest(key: "brand", type: FilterType.TERM, values: ["Apple"]),
                            new FilterRequest(key: "operating_system", type: FilterType.TERM, values: ["Windows"])
                    ] |
                    '{"totalElements": 0, "products": []}'

            "Numeric field filtering" |
                    [new FilterRequest(key: "ram", type: FilterType.TERM, values: ["16"])] |
                    '{"totalElements": 1, "products": [{"id": 20001}]}'

            "Array field item filtering" |
                    [new FilterRequest(key: "features", type: FilterType.TERM, values: ["Touchscreen"])] |
                    '{"totalElements": 1, "products": [{"id": 20001}]}'

            "Resilience against empty values list" |
                    [new FilterRequest(key: "brand", type: FilterType.TERM, values: [])] |
                    '{"totalElements": 2}'
    }

    @Unroll
    def "Happy path - RANGE filter for RAM between #minRam and #maxRam returns expected results"() {
        given: "three laptops with different RAM specifications indexed directly in Elasticsearch"
            def ram8 = ProductDocument.builder()
                    .id(10003L)
                    .title("Basic Laptop")
                    .category("laptops")
                    .brand("Apple")
                    .price(800.00.toBigDecimal())
                    .specifications(["ram": 8, "display": 15.5])
                    .build()
            def ram16 = ProductDocument.builder()
                    .id(10004L)
                    .title("Mid Laptop")
                    .category("laptops")
                    .brand("Apple")
                    .price(1200.00.toBigDecimal())
                    .specifications(["ram": 16, "display": 14.0])
                    .build()
            def ram32 = ProductDocument.builder()
                    .id(10005L)
                    .title("Pro Laptop")
                    .category("laptops")
                    .brand("Apple")
                    .price(2000.00.toBigDecimal())
                    .specifications(["ram": 32, "display": 16.0])
                    .build()

            productElasticsearchRepository.saveAll([ram8, ram16, ram32])
            Thread.sleep(200)

            // Metadata map to dynamically construct the expected JSON text
            def productMetadata = [
                    10003L: [title: "Basic Laptop", price: 800.00, brand: "Apple"],
                    10004L: [title: "Mid Laptop", price: 1200.00, brand: "Apple"],
                    10005L: [title: "Pro Laptop", price: 2000.00, brand: "Apple"]
            ]

        when: "searching for ram between min and max thresholds"
            def filter = new FilterRequest(key: "ram", type: FilterType.RANGE, min: minRam, max: maxRam)
            def request = new ProductSearchRequest(category: "laptops", filters: [filter])
            def response = webActor.searchProducts(request)

        then: "response is 200 OK"
            response.expectStatus().isOk()

        and: "the JSON response precisely matches the expected structural layout"
            def body = response.expectBody(String).returnResult().getResponseBody()

            // 1. Dynamically build expected products array
            def productsJsonArray = productIds.collect { id ->
                def info = productMetadata[id]
                """
                {
                    "id": ${id},
                    "title": "${info.title}",
                    "category": "laptops",
                    "price": ${info.price},
                    "brand": "${info.brand}"
                }
                """
            }.join(",")

            // 2. Dynamically build expected ram facet array
            def ramFacetsJsonArray = filterRamValues.collect { val ->
                """{ "value": "${val}", "count": 1 }"""
            }.join(",")

            // 3. Dynamically build expected display facet array
            def displayFacetsJsonArray = filterDisplayValues.collect { val ->
                """{ "value": "${val}", "count": 1 }"""
            }.join(",")

            // 4. Assemble the full expected payload
            def expectedBody = """
                {
                    "products": [ ${productsJsonArray} ],
                    "filters": {
                        "brand": [],
                        "operating_system": [],
                        "ram": [ ${ramFacetsJsonArray} ],
                        "display": [ ${displayFacetsJsonArray} ],
                        "release_year": [],
                        "features": []
                    },
                    "totalElements": ${productIds.size()},
                    "totalPages": 1,
                    "page": 0,
                    "size": 20
                }
                """

            JSONAssert.assertEquals(expectedBody, body, JSONCompareMode.LENIENT)

        where:
            minRam | maxRam | productIds       | filterRamValues | filterDisplayValues
            10.0   | 20.0   | [10004L]         | ["16"]          | ["14.0"]
            null   | 20.0   | [10003L, 10004L] | ["8", "16"]     | ["14.0", "15.5"]
            10.0   | null   | [10004L, 10005L] | ["16", "32"]    | ["14.0", "16.0"]
    }

    @Unroll
    def "Happy path - complex multi-filter filtering with arrays and ranges for #scenario"() {
        given: "a robust inventory of 6 diverse laptops with feature arrays indexed in Elasticsearch"
            def apple16 = ProductDocument.builder()
                    .id(50001L).title("MacBook Pro 16").category("laptops").price(2499.99).brand("Apple")
                    .specifications([
                            "brand": "Apple", "operating_system": "macOS", "ram": 16,
                            "features": ["Touch Bar", "Liquid Retina", "Thunderbolt"]
                    ]).build()
            def apple8 = ProductDocument.builder()
                    .id(50002L).title("MacBook Air 13").category("laptops").price(999.99).brand("Apple")
                    .specifications([
                            "brand": "Apple", "operating_system": "macOS", "ram": 8,
                            "features": ["Liquid Retina", "Fanless", "Thunderbolt"]
                    ]).build()
            def dell16 = ProductDocument.builder()
                    .id(50003L).title("Dell XPS 15").category("laptops").price(1899.99).brand("Dell")
                    .specifications([
                            "brand": "Dell", "operating_system": "Windows", "ram": 16,
                            "features": ["Touchscreen", "OLED Screen", "Thunderbolt"]
                    ]).build()
            def dell32 = ProductDocument.builder()
                    .id(50004L).title("Dell Precision").category("laptops").price(2999.99).brand("Dell")
                    .specifications([
                            "brand": "Dell", "operating_system": "Windows", "ram": 32,
                            "features": ["ECC Memory", "OLED Screen", "Stylus Support"]
                    ]).build()
            def lenovo16 = ProductDocument.builder()
                    .id(50005L).title("Lenovo ThinkPad").category("laptops").price(1299.99).brand("Lenovo")
                    .specifications([
                            "brand": "Lenovo", "operating_system": "Windows", "ram": 16,
                            "features": ["TrackPoint", "Privacy Shutter", "Touchscreen"]
                    ]).build()
            def lenovo8 = ProductDocument.builder()
                    .id(50006L).title("Lenovo IdeaPad").category("laptops").price(499.99).brand("Lenovo")
                    .specifications([
                            "brand": "Lenovo", "operating_system": "Windows", "ram": 8,
                            "features": ["Privacy Shutter", "Anodized Aluminum"]
                    ]).build()

            productElasticsearchRepository.saveAll([apple16, apple8, dell16, dell32, lenovo16, lenovo8])
            Thread.sleep(200)

            // Metadata map to dynamically construct the expected JSON text
            def productMetadata = [
                    50001L: [title: "MacBook Pro 16", price: 2499.99, brand: "Apple"],
                    50002L: [title: "MacBook Air 13", price: 999.99, brand: "Apple"],
                    50003L: [title: "Dell XPS 15", price: 1899.99, brand: "Dell"],
                    50004L: [title: "Dell Precision", price: 2999.99, brand: "Dell"],
                    50005L: [title: "Lenovo ThinkPad", price: 1299.99, brand: "Lenovo"],
                    50006L: [title: "Lenovo IdeaPad", price: 499.99, brand: "Lenovo"]
            ]

        when: "searching with complex combinations of array matching and ranges"
            def request = new ProductSearchRequest(category: "laptops", filters: complexFilters)
            def response = webActor.searchProducts(request)

        then: "response is 200 OK"
            response.expectStatus().isOk()

        and: "the JSON response matches the precise matrix of matching items and facet buckets"
            def body = response.expectBody(String).returnResult().getResponseBody()

            // 1. Dynamically build expected products array
            def productsJsonArray = expectedIds.collect { id ->
                def info = productMetadata[id]
                """
                {
                    "id": ${id},
                    "title": "${info.title}",
                    "category": "laptops",
                    "price": ${info.price},
                    "brand": "${info.brand}"
                }
                """
            }.join(",")

            // 2. Dynamically build expected facet strings from map lists
            def brandFacets = expectedBrands.collect { k, v -> """{"value":"${k}","count":${v}}""" }.join(",")
            def osFacets = expectedOs.collect { k, v -> """{"value":"${k}","count":${v}}""" }.join(",")
            def ramFacets = expectedRams.collect { k, v -> """{"value":"${k}","count":${v}}""" }.join(",")
            def featureFacets = expectedFeatures.collect { k, v -> """{"value":"${k}","count":${v}}""" }.join(",")

            // 3. Assemble the full expected payload matrix
            def expectedBody = """
                {
                    "products": [ ${productsJsonArray} ],
                    "filters": {
                        "brand": [ ${brandFacets} ],
                        "operating_system": [ ${osFacets} ],
                        "ram": [ ${ramFacets} ],
                        "display": [],
                        "release_year": [],
                        "features": [ ${featureFacets} ]
                    },
                    "totalElements": ${expectedIds.size()},
                    "totalPages": 1,
                    "page": 0,
                    "size": 20
                }
                """

            JSONAssert.assertEquals(expectedBody, body, JSONCompareMode.LENIENT)

        where:
            scenario | complexFilters | expectedIds | expectedBrands | expectedOs | expectedRams | expectedFeatures

            "Array matching (TERM) AND RAM Range (RANGE)" |
                    [
                            new FilterRequest(key: "features", type: FilterType.TERM, values: ["Thunderbolt"]),
                            new FilterRequest(key: "ram", type: FilterType.RANGE, min: 12.0, max: 32.0)
                    ] |
                    [50001L, 50003L] |
                    ["Apple": 1, "Dell": 1] |
                    ["Windows": 1, "macOS": 1] |
                    ["16": 2] |
                    ["Thunderbolt": 2, "Liquid Retina": 1, "OLED Screen": 1, "Touch Bar": 1, "Touchscreen": 1]

            "Multiple Brands (TERM) AND Array value (TERM)" |
                    [
                            new FilterRequest(key: "brand", type: FilterType.TERM, values: ["Dell", "Lenovo"]),
                            new FilterRequest(key: "features", type: FilterType.TERM, values: ["Touchscreen"])
                    ] |
                    [50003L, 50005L] |
                    ["Dell": 1, "Lenovo": 1] |
                    ["Windows": 2] |
                    ["16": 2] |
                    ["Touchscreen": 2, "OLED Screen": 1, "Privacy Shutter": 1, "Thunderbolt": 1, "TrackPoint": 1]

            "Array matching (TERM) AND High RAM (RANGE) narrow match" |
                    [
                            new FilterRequest(key: "features", type: FilterType.TERM, values: ["OLED Screen"]),
                            new FilterRequest(key: "ram", type: FilterType.RANGE, min: 24.0, max: 64.0)
                    ] |
                    [50004L] |
                    ["Dell": 1] |
                    ["Windows": 1] |
                    ["32": 1] |
                    ["ECC Memory": 1, "OLED Screen": 1, "Stylus Support": 1]
    }

    def "Unhappy path - blank category returns 400 Bad Request"() {
        when: "searching with a blank category"
            def request = new ProductSearchRequest(category: "")
            def response = webActor.searchProducts(request)

        then: "response is 400 Bad Request"
            response.expectStatus().isBadRequest()
    }

    def "Unhappy path - negative page value returns 400 Bad Request"() {
        when: "searching with page = -1"
            def request = new ProductSearchRequest(category: "laptops", page: -1)
            def response = webActor.searchProducts(request)

        then: "response is 400 Bad Request"
            response.expectStatus().isBadRequest()
    }

    def "Unhappy path - size of zero returns 400 Bad Request"() {
        when: "searching with size = 0"
            def request = new ProductSearchRequest(category: "laptops", size: 0)
            def response = webActor.searchProducts(request)

        then: "response is 400 Bad Request"
            response.expectStatus().isBadRequest()
    }
}