package org.skroutz.scraper.skroutzwebscraper.search.application.service

import ch.qos.logback.classic.Level
import org.skroutz.scraper.skroutzwebscraper.base.WithLoggingBaseSpec
import org.skroutz.scraper.skroutzwebscraper.product.domain.entity.Product
import org.skroutz.scraper.skroutzwebscraper.search.domain.entity.ProductDocument
import org.skroutz.scraper.skroutzwebscraper.search.domain.repository.ProductElasticsearchRepository
import org.skroutz.scraper.skroutzwebscraper.search.infrastructure.mapper.ProductDocumentMapper
import spock.lang.Subject

class ProductIndexingServiceSpec extends WithLoggingBaseSpec {

    ProductElasticsearchRepository productElasticsearchRepository = Mock(ProductElasticsearchRepository)
    ProductDocumentMapper productDocumentMapper = Mock(ProductDocumentMapper)

    @Subject
    ProductIndexingService service = new ProductIndexingService(productElasticsearchRepository, productDocumentMapper)

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
