package org.skroutz.scraper.skroutzwebscraper.service

import ch.qos.logback.classic.Level
import org.skroutz.scraper.skroutzwebscraper.base.WithLoggingBaseSpec
import org.skroutz.scraper.skroutzwebscraper.document.ProductDocument
import org.skroutz.scraper.skroutzwebscraper.dto.ProductSuggestionDto
import org.skroutz.scraper.skroutzwebscraper.entity.Product
import org.skroutz.scraper.skroutzwebscraper.mapper.ProductDocumentMapper
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

    @Subject
    ProductSearchService service = new ProductSearchService(productElasticsearchRepository, productDocumentMapper, elasticsearchOperations)

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

    def "Happy path, should return product suggestions"() {
        given: "mocked search results with 2 products"
            def documents = [
                    createProductDocument(1L, "MacBook Pro 16-inch"),
                    createProductDocument(2L, "MacBook Air 13-inch")
            ]
            def dtos = [
                    createSuggestionDto(1L, "MacBook Pro 16-inch"),
                    createSuggestionDto(2L, "MacBook Air 13-inch")
            ]
            mockSearchResults(documents, dtos)

        when: "getting product suggestions"
            def result = service.getProductSuggestions("macbook", 5)

        then: "should return the mapped suggestions"
            result == dtos
    }

    def "Happy path, should return empty list when no matches found"() {
        given: "empty search results"
            mockSearchResults([], [])

        when: "getting product suggestions"
            def result = service.getProductSuggestions("nonexistent", 5)

        then: "should return empty list"
            result.isEmpty()
    }

    def "Happy path, should respect the limit parameter"() {
        given: "mocked search results with 3 products"
            def documents = [
                    createProductDocument(1L, "MacBook Pro 16-inch"),
                    createProductDocument(2L, "MacBook Air 13-inch"),
                    createProductDocument(3L, "MacBook Pro 14-inch")
            ]
            def dtos = [
                    createSuggestionDto(1L, "MacBook Pro 16-inch"),
                    createSuggestionDto(2L, "MacBook Air 13-inch"),
                    createSuggestionDto(3L, "MacBook Pro 14-inch")
            ]

        when: "getting product suggestions with limit of 3"
            def result = service.getProductSuggestions("macbook", 3)

        then: "should execute search with correct limit"
            1 * elasticsearchOperations.search(_, ProductDocument.class) >> { args ->
                assert args[0].maxResults == 3
                createMockSearchHits(documents, dtos)
            }

        and: "should return 3 suggestions"
            result.size() == 3
    }

    private ProductDocument createProductDocument(Long id, String title) {
        ProductDocument.builder()
                .id(id)
                .title(title)
                .url("http://example.com/${title.replaceAll(' ', '-').toLowerCase()}")
                .build()
    }

    private ProductSuggestionDto createSuggestionDto(Long id, String title) {
        ProductSuggestionDto.builder()
                .id(id)
                .title(title)
                .build()
    }

    private void mockSearchResults(List<ProductDocument> documents, List<ProductSuggestionDto> dtos) {
        elasticsearchOperations.search(_, ProductDocument.class) >> createMockSearchHits(documents, dtos)
    }

    private SearchHits<ProductDocument> createMockSearchHits(List<ProductDocument> documents, List<ProductSuggestionDto> dtos) {
        SearchHits<ProductDocument> searchHits = Mock(SearchHits)

        searchHits.map(_) >> { args ->
            def mapper = args[0]
            def mappedResults = documents.collect { doc ->
                SearchHit<ProductDocument> hit = Mock(SearchHit)
                hit.getContent() >> doc
                mapper.apply(hit)
            }
            Streamable.of(mappedResults)
        }

        documents.eachWithIndex { doc, idx ->
            productDocumentMapper.toSuggestionDto(doc) >> dtos[idx]
        }

        return searchHits
    }
}
