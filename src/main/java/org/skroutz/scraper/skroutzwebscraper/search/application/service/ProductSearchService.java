package org.skroutz.scraper.skroutzwebscraper.search.application.service;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.skroutz.scraper.skroutzwebscraper.category.domain.entity.CategorySchema;
import org.skroutz.scraper.skroutzwebscraper.category.domain.repository.CategorySchemaRepository;
import org.skroutz.scraper.skroutzwebscraper.category.domain.schema.FieldType;
import org.skroutz.scraper.skroutzwebscraper.search.domain.entity.ProductDocument;
import org.skroutz.scraper.skroutzwebscraper.search.infrastructure.mapper.ProductDocumentMapper;
import org.skroutz.scraper.skroutzwebscraper.search.infrastructure.dto.ProductSuggestionDto;
import org.skroutz.scraper.skroutzwebscraper.search.infrastructure.dto.SimilarProductsResponse;
import org.skroutz.scraper.skroutzwebscraper.search.infrastructure.dto.*;
import org.skroutz.scraper.skroutzwebscraper.search.infrastructure.es.ProductAggregationProcessor;
import org.skroutz.scraper.skroutzwebscraper.search.infrastructure.es.ProductQueryBuilder;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductSearchService {

    private final ProductDocumentMapper productDocumentMapper;
    private final ElasticsearchOperations elasticsearchOperations;
    private final CategorySchemaRepository categorySchemaRepository;
    private final ProductQueryBuilder productQueryBuilder;
    private final ProductAggregationProcessor productAggregationProcessor;

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

    public SimilarProductsResponse findSimilar(Long productId, int limit) {
        ProductDocument sourceDoc = elasticsearchOperations.get(String.valueOf(productId), ProductDocument.class);
        if (sourceDoc == null) {
            return SimilarProductsResponse.builder()
                    .sourceProductId(productId)
                    .products(List.of())
                    .totalElements(0)
                    .build();
        }

        NativeQuery nativeQuery = productQueryBuilder.buildSimilarProductsQuery(productId, sourceDoc, limit);

        SearchHits<ProductDocument> searchHits = elasticsearchOperations.search(nativeQuery, ProductDocument.class);

        List<ProductItemDto> products = searchHits.getSearchHits().stream()
                .map(SearchHit::getContent)
                .map(productDocumentMapper::toItemDto)
                .toList();

        return SimilarProductsResponse.builder()
                .sourceProductId(productId)
                .products(products)
                .totalElements((int) searchHits.getTotalHits())
                .build();
    }

    public ProductSearchResponse search(ProductSearchRequest request) {
        int page = request.getPage() != null ? request.getPage() : 0;
        int size = request.getSize() != null ? request.getSize() : 20;

        Map<String, String> specKeyToField = resolveSpecFieldMap(request.getCategory());

        NativeQuery nativeQuery = productQueryBuilder.buildSearchQuery(request, specKeyToField, page, size);

        SearchHits<ProductDocument> searchHits =
                elasticsearchOperations.search(nativeQuery, ProductDocument.class);

        List<ProductDocument> products = searchHits.getSearchHits().stream()
                .map(SearchHit::getContent)
                .toList();
        List<ProductItemDto> productItemDtos = products.stream()
                .map(productDocumentMapper::toItemDto)
                .toList();

        Map<String, List<SpecFacetBucketDto>> facets =
                productAggregationProcessor.extractFacets(searchHits, specKeyToField.keySet());

        long totalElements = searchHits.getTotalHits();
        int totalPages = (int) Math.ceil((double) totalElements / size);

        return ProductSearchResponse.builder()
                .products(productItemDtos)
                .filters(facets)
                .totalElements(totalElements)
                .page(page)
                .size(size)
                .totalPages(totalPages)
                .build();
    }

    private Map<String, String> resolveSpecFieldMap(String category) {
        return categorySchemaRepository.findByCategory(category)
                .map(CategorySchema::getSchema)
                .map(schema -> {
                    Map<String, String> map = new LinkedHashMap<>();

                    schema.getDirectFields().forEach(f -> {
                        String esField = (f.getType() == FieldType.STRING || f.getType() == null)
                                ? "specifications." + f.getTarget() + ".keyword"
                                : "specifications." + f.getTarget();
                        map.putIfAbsent(f.getTarget(), esField);
                    });

                    schema.getArrayFields().forEach(f ->
                            map.putIfAbsent(f.getTarget(), "specifications." + f.getTarget() + ".keyword"));

                    return map;
                })
                .orElse(Map.of());
    }
}
