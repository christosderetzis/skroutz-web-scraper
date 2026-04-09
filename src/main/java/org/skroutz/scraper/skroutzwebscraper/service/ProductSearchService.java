package org.skroutz.scraper.skroutzwebscraper.service;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.skroutz.scraper.skroutzwebscraper.document.ProductDocument;
import org.skroutz.scraper.skroutzwebscraper.dto.ProductSuggestionDto;
import org.skroutz.scraper.skroutzwebscraper.entity.Product;
import org.skroutz.scraper.skroutzwebscraper.mapper.ProductDocumentMapper;
import org.skroutz.scraper.skroutzwebscraper.repository.ProductElasticsearchRepository;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductSearchService {

    private final ProductElasticsearchRepository productElasticsearchRepository;
    private final ProductDocumentMapper productDocumentMapper;
    private final ElasticsearchOperations elasticsearchOperations;

    public Boolean indexProduct(Product product) {
        try {
            ProductDocument document = productDocumentMapper.toDocument(product);
            productElasticsearchRepository.save(document);
            log.info("Indexed product {} to Elasticsearch", product.getId());
            return true;
        } catch (Exception e) {
            log.error("Failed to index product {} to Elasticsearch: {}", product.getId(), e.getMessage(), e);
            return false;
        }
    }

    public void indexProducts(List<Product> products) {
        try {
            List<ProductDocument> documents = products.stream()
                    .map(productDocumentMapper::toDocument)
                    .toList();
            productElasticsearchRepository.saveAll(documents);
            log.info("Indexed {} products to Elasticsearch", products.size());
        } catch (Exception e) {
            log.error("Failed to index products to Elasticsearch: {}", e.getMessage(), e);
        }
    }

    public List<ProductSuggestionDto> getProductSuggestions(String prefix, Integer limit) {
        NativeQuery query = NativeQuery.builder()
                .withQuery(Query.of(q -> q
                        .matchPhrasePrefix(mp -> mp
                                .field("title.autocomplete")
                                .query(prefix)
                        )
                ))
                .withMaxResults(limit)
                .build();

        List<ProductDocument> searchResults = elasticsearchOperations.search(query, ProductDocument.class)
                .map(SearchHit::getContent)
                .toList();

        return searchResults.stream()
                .map(productDocumentMapper::toSuggestionDto)
                .toList();
    }
}
