package org.skroutz.scraper.skroutzwebscraper.service

import ch.qos.logback.classic.Level
import org.skroutz.scraper.skroutzwebscraper.base.WithLoggingBaseSpec
import org.skroutz.scraper.skroutzwebscraper.document.ProductDocument
import org.skroutz.scraper.skroutzwebscraper.dto.ProductSuggestionDto
import org.skroutz.scraper.skroutzwebscraper.entity.Product
import org.skroutz.scraper.skroutzwebscraper.mapper.ProductDocumentMapper
import org.skroutz.scraper.skroutzwebscraper.repository.CategorySchemaRepository
import org.skroutz.scraper.skroutzwebscraper.repository.ProductElasticsearchRepository
import org.springframework.data.elasticsearch.core.ElasticsearchOperations
import org.springframework.data.elasticsearch.core.SearchHit
import org.springframework.data.elasticsearch.core.SearchHits
import org.springframework.data.util.Streamable
import spock.lang.Subject

class ProductSearchServiceSpec extends WithLoggingBaseSpec {

    ProductElasticsearchRepository productElasticsearchRepository = Mock(ProductElasticsearchRepository)
    ProductDocumentMapper productDocumentMapper = Mock(ProductDocumentMapper)
    ElasticsearchOperations elasticsearchOperations = Mock(ElasticsearchOperations)
    CategorySchemaRepository categorySchemaRepository = Mock(CategorySchemaRepository)

    @Subject
    ProductSearchService service = new ProductSearchService(productElasticsearchRepository, productDocumentMapper, elasticsearchOperations, categorySchemaRepository)

    def "Happy path, should index a single product and return true"() {
        given: "a product to index"
            Product product = new Product(id: 1L, title: "Test Product", url: "http://example.com/product1")

        and: "a mapped document"
            ProductDocument document = ProductDocument.builder()
                    .id(1L)
                    .title("Test Product")
                    .url("http://example.com/product1")
                    .build()

        when: "indexing the product"
            Boolean result = service.indexProduct(product)

        then: "the product should be mapped and saved"
            1 * productDocumentMapper.toDocument(product) >> document
            1 * productElasticsearchRepository.save(document) >> document
            0 * _

        and: "should return true"
            result == true

        and: "should log success message"
            assertLog(Level.INFO, "Indexed product 1 to Elasticsearch")
    }

    def "Unhappy path, should return false when indexing fails"() {
        given: "a product to index"
            Product product = new Product(id: 1L, title: "Test Product", url: "http://example.com/product1")

        and: "a mapped document"
            ProductDocument document = ProductDocument.builder()
                    .id(1L)
                    .title("Test Product")
                    .url("http://example.com/product1")
                    .build()

        when: "indexing the product"
            Boolean result = service.indexProduct(product)

        then: "the mapping succeeds but repository throws exception"
            1 * productDocumentMapper.toDocument(product) >> document
            1 * productElasticsearchRepository.save(document) >> { throw new RuntimeException("Elasticsearch connection failed") }
            0 * _

        and: "should return false"
            result == false

        and: "should log error message"
            assertLog(Level.ERROR, "Failed to index product 1 to Elasticsearch")
    }

    def "Unhappy path, should return false when mapping fails"() {
        given: "a product to index"
            Product product = new Product(id: 1L, title: "Test Product", url: "http://example.com/product1")

        when: "indexing the product"
            Boolean result = service.indexProduct(product)

        then: "the mapping throws exception"
            1 * productDocumentMapper.toDocument(product) >> { throw new RuntimeException("Mapping failed") }
            0 * _

        and: "should return false"
            result == false

        and: "should log error message"
            assertLog(Level.ERROR, "Failed to index product 1 to Elasticsearch")
    }

    def "Happy path, should index multiple products"() {
        given: "a list of products to index"
            List<Product> products = [
                    new Product(id: 1L, title: "Product 1", url: "http://example.com/product1"),
                    new Product(id: 2L, title: "Product 2", url: "http://example.com/product2")
            ]

        and: "mapped documents"
            List<ProductDocument> documents = [
                    ProductDocument.builder().id(1L).title("Product 1").url("http://example.com/product1").build(),
                    ProductDocument.builder().id(2L).title("Product 2").url("http://example.com/product2").build()
            ]

        when: "indexing the products"
            service.indexProducts(products)

        then: "the products should be mapped and saved"
            1 * productDocumentMapper.toDocument(products[0]) >> documents[0]
            1 * productDocumentMapper.toDocument(products[1]) >> documents[1]
            1 * productElasticsearchRepository.saveAll(_) >> documents
            0 * _

        and: "should log success message"
            assertLog(Level.INFO, "Indexed 2 products to Elasticsearch")
    }

    def "Happy path, should handle empty product list"() {
        given: "an empty list of products"
            List<Product> products = []

        when: "indexing the products"
            service.indexProducts(products)

        then: "should save empty list"
            1 * productElasticsearchRepository.saveAll([]) >> []
            0 * _

        and: "should log success message"
            assertLog(Level.INFO, "Indexed 0 products to Elasticsearch")
    }

    def "Unhappy path, should log error when indexing multiple products fails"() {
        given: "a list of products to index"
            List<Product> products = [
                    new Product(id: 1L, title: "Product 1", url: "http://example.com/product1"),
                    new Product(id: 2L, title: "Product 2", url: "http://example.com/product2")
            ]

        and: "mapped documents"
            List<ProductDocument> documents = [
                    ProductDocument.builder().id(1L).title("Product 1").url("http://example.com/product1").build(),
                    ProductDocument.builder().id(2L).title("Product 2").url("http://example.com/product2").build()
            ]

        when: "indexing the products"
            service.indexProducts(products)

        then: "the products are mapped but repository throws exception"
            1 * productDocumentMapper.toDocument(products[0]) >> documents[0]
            1 * productDocumentMapper.toDocument(products[1]) >> documents[1]
            1 * productElasticsearchRepository.saveAll(_) >> { throw new RuntimeException("Elasticsearch bulk indexing failed") }
            0 * _

        and: "should log error message"
            assertLog(Level.ERROR, "Failed to index products to Elasticsearch")
    }

    def "Unhappy path, should log error when mapping fails during batch indexing"() {
        given: "a list of products to index"
            List<Product> products = [
                    new Product(id: 1L, title: "Product 1", url: "http://example.com/product1")
            ]

        when: "indexing the products"
            service.indexProducts(products)

        then: "the mapping throws exception"
            1 * productDocumentMapper.toDocument(products[0]) >> { throw new RuntimeException("Mapping failed") }
            0 * _

        and: "should log error message"
            assertLog(Level.ERROR, "Failed to index products to Elasticsearch")
    }
}
