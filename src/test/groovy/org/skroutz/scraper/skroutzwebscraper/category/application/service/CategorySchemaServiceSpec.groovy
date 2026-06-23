package org.skroutz.scraper.skroutzwebscraper.category.application.service

import ch.qos.logback.classic.Level
import org.skroutz.scraper.skroutzwebscraper.base.WithLoggingBaseSpec
import org.skroutz.scraper.skroutzwebscraper.category.domain.entity.CategorySchema
import org.skroutz.scraper.skroutzwebscraper.category.domain.repository.CategorySchemaRepository
import org.skroutz.scraper.skroutzwebscraper.category.domain.schema.CategoryMappingSchema
import org.skroutz.scraper.skroutzwebscraper.category.domain.schema.DirectFieldMapping
import org.skroutz.scraper.skroutzwebscraper.category.infrastructure.dto.CategorySchemaCreateRequestDto
import org.skroutz.scraper.skroutzwebscraper.category.infrastructure.exception.CategorySchemaNotFoundException
import org.skroutz.scraper.skroutzwebscraper.category.infrastructure.exception.DuplicateCategoryException
import spock.lang.Subject

class CategorySchemaServiceSpec extends WithLoggingBaseSpec {

    CategorySchemaRepository categorySchemaRepository = Mock()

    @Subject
    CategorySchemaService service = new CategorySchemaService(categorySchemaRepository)

    def "create - success"() {
        given:
        def createdSchema = CategoryMappingSchema.builder()
                .directFields([DirectFieldMapping.builder().path("span.brand").target("brand").build()])
                .arrayFields([])
                .build()
        def request = new CategorySchemaCreateRequestDto("electronics", createdSchema)
        def savedEntity = CategorySchema.builder()
                .id(1L)
                .category("electronics")
                .schema(createdSchema)
                .version(1)
                .build()

        when:
        def result = service.create(request)

        then:
        1 * categorySchemaRepository.existsByCategory("electronics") >> false
        1 * categorySchemaRepository.save({ it.category == "electronics" && it.schema == createdSchema }) >> savedEntity
        0 * _

        and:
        with(result) {
            id == 1L
            category == "electronics"
            schema == createdSchema
            version == 1
        }

        and:
        assertLog(Level.INFO, "Created category schema for category: electronics")
    }

    def "create - throws DuplicateCategoryException when category already exists"() {
        given:
            def request = new CategorySchemaCreateRequestDto("dup-category", CategoryMappingSchema.builder().build())

        when:
            service.create(request)

        then:
            1 * categorySchemaRepository.existsByCategory("dup-category") >> true
            0 * categorySchemaRepository.save(_)
            thrown(DuplicateCategoryException)
    }

    def "getByCategory - success"() {
        given:
            def mappingSchema = CategoryMappingSchema.builder()
                    .directFields([DirectFieldMapping.builder().path("span.price").target("price").build()])
                    .arrayFields([])
                    .build()
            def entity = CategorySchema.builder()
                    .id(1L)
                    .category("electronics")
                    .schema(mappingSchema)
                    .version(1)
                    .build()

        when:
            def result = service.getByCategory("electronics")

        then:
            1 * categorySchemaRepository.findByCategory("electronics") >> Optional.of(entity)
            0 * _

        and:
            with(result) {
                id == 1L
                category == "electronics"
                schema == mappingSchema
                version == 1
            }
    }

    def "getByCategory - throws CategorySchemaNotFoundException when category does not exist"() {
        when:
            service.getByCategory("non-existent")

        then:
            1 * categorySchemaRepository.findByCategory("non-existent") >> Optional.empty()
            thrown(CategorySchemaNotFoundException)
    }
}
