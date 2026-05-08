package org.skroutz.scraper.skroutzwebscraper.specs

import org.skroutz.scraper.skroutzwebscraper.dto.CategorySchemaCreateRequestDto
import org.skroutz.scraper.skroutzwebscraper.schema.CategoryMappingSchema
import org.skroutz.scraper.skroutzwebscraper.schema.DirectFieldMapping
import org.skroutz.scraper.skroutzwebscraper.schema.FieldType
import org.skroutz.scraper.skroutzwebscraper.utils.base.BaseFunctionalSpec
import org.skyscreamer.jsonassert.JSONAssert
import org.skyscreamer.jsonassert.JSONCompareMode

class CategorySchemaFunctionalSpec extends BaseFunctionalSpec {

    def "create category schema - success"() {
        given: "a valid category schema creation request"
            def schema = CategoryMappingSchema.builder()
                    .directFields([
                            DirectFieldMapping.builder().path("span.brand").target("brand").type(FieldType.INTEGER).build(),
                            DirectFieldMapping.builder().path("span.model").target("model").build()
                    ])
                    .arrayFields([])
                    .build()
            def request = new CategorySchemaCreateRequestDto("electronics", schema)

        when: "creating a new category schema"
            def response = webActor.createCategorySchema(request)

        then: "the response is 201 Created with the created schema in the body"
            response.expectStatus().isCreated()
            String body = response.expectBody(String).returnResult().getResponseBody()
            JSONAssert.assertEquals("""
                {
                    "category": "electronics",
                    "version": 1,
                    "schema": {
                        "directFields": [
                            {"path": "span.brand", "target": "brand", "type": "INTEGER"},
                            {"path": "span.model", "target": "model", "type": "STRING"}
                        ],
                        "arrayFields": []
                    }
                }
            """, body, JSONCompareMode.LENIENT)

        and: "schema is persisted in the database"
            def saved = categorySchemaRepository.findByCategory("electronics")
            saved.isPresent()
            with(saved.get()) {
                category == "electronics"
                version == 1
            }
    }

    def "create category schema - duplicate returns 409 conflict"() {
        given: "an existing category schema"
            def request = new CategorySchemaCreateRequestDto("dup-category", CategoryMappingSchema.builder().build())
            webActor.createCategorySchema(request).expectStatus().isCreated()

        when: "attempting to create a duplicate"
            def response = webActor.createCategorySchema(request)

        then: "the response is 409 Conflict with an appropriate error message"
            response.expectStatus().isEqualTo(409)
            String body = response.expectBody(String).returnResult().getResponseBody()
            JSONAssert.assertEquals("""
                {
                    "status": 409,
                    "method": "POST",
                    "errors": ["Category schema already exists for category: dup-category"],
                    "path": "/category-schemas"
                }
            """, body, JSONCompareMode.LENIENT)
    }

    def "create category schema - blank category returns 400"() {
        given: "a request with blank category"
            def request = new CategorySchemaCreateRequestDto("", CategoryMappingSchema.builder().build())

        when: "attempting to create a category schema with blank category"
            def response = webActor.createCategorySchema(request)

        then: "the response is 400 Bad Request with an appropriate error message"
            response.expectStatus().isBadRequest()
            String body = response.expectBody(String).returnResult().getResponseBody()
            JSONAssert.assertEquals("""
                {
                    "status": 400,
                    "method": "POST",
                    "errors": ["category: Category must not be blank"],
                    "path": "/category-schemas"
                }
            """, body, JSONCompareMode.LENIENT)
    }

    def "create category schema - null schema returns 400"() {
        given: "a request with null schema"
            def request = new CategorySchemaCreateRequestDto("some-category", null)

        when: "attempting to create a category schema with null schema"
            def response = webActor.createCategorySchema(request)

        then: "the response is 400 Bad Request with an appropriate error message"
            response.expectStatus().isBadRequest()
            String body = response.expectBody(String).returnResult().getResponseBody()
            JSONAssert.assertEquals("""
                {
                    "status": 400,
                    "method": "POST",
                    "errors": ["schema: Schema must not be null"],
                    "path": "/category-schemas"
                }
            """, body, JSONCompareMode.LENIENT)
    }

    def "get category schema by category - success"() {
        given: "an existing category schema"
            def schema = CategoryMappingSchema.builder()
                    .directFields([DirectFieldMapping.builder().path("span.price").target("price").build()])
                    .arrayFields([])
                    .build()
            webActor.createCategorySchema(new CategorySchemaCreateRequestDto("get-cat", schema))
                    .expectStatus().isCreated()

        when: "retrieving the category schema by category"
            def response = webActor.getCategorySchema("get-cat")

        then: "the response is successful and contains the correct schema"
            response.expectStatus().isOk()
            String body = response.expectBody(String).returnResult().getResponseBody()
            JSONAssert.assertEquals("""
                {
                    "category": "get-cat",
                    "version": 1,
                    "schema": {
                        "directFields": [
                            {"path": "span.price", "target": "price", "type": "STRING"}
                        ],
                        "arrayFields": []
                    }
                }
            """, body, JSONCompareMode.LENIENT)
    }

    def "get category schema by category - not found"() {
        when: "retrieving a non-existent category schema"
            def response = webActor.getCategorySchema("non-existent-category")

        then: "the response is 404 Not Found with an appropriate error message"
            response.expectStatus().isNotFound()
            String body = response.expectBody(String).returnResult().getResponseBody()
            JSONAssert.assertEquals("""
                {
                    "status": 404,
                    "method": "GET",
                    "errors": ["Category schema not found for category: non-existent-category"],
                    "path": "/category-schemas/non-existent-category"
                }
            """, body, JSONCompareMode.LENIENT)
    }
}
