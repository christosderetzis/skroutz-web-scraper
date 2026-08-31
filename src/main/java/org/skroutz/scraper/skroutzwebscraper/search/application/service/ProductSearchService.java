package org.skroutz.scraper.skroutzwebscraper.search.application.service;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
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
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
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

    public SimilarProductsResponse findSimilarProducts(Long productId, int limit) {
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

        // if category is not specified, try to find a single matching category from the search query
        String facetCategory = StringUtils.isNotBlank(request.getCategory())
                ? request.getCategory()
                : findSingleMatchingCategory(request);

        // if the category is still not specified, search all products
        boolean crossCategorySearch = facetCategory == null;
        Map<String, String> specKeyToField = crossCategorySearch
                ? Map.of()
                : resolveSpecFieldMap(facetCategory);

        NativeQuery nativeQuery = productQueryBuilder.buildSearchQuery(request, facetCategory, specKeyToField, page, size);

        SearchHits<ProductDocument> searchHits =
                elasticsearchOperations.search(nativeQuery, ProductDocument.class);

        List<ProductDocument> products = searchHits.getSearchHits().stream()
                .map(SearchHit::getContent)
                .toList();
        List<ProductItemDto> productItemDtos = products.stream()
                .map(productDocumentMapper::toItemDto)
                .toList();

        Set<String> specKeys;
        if (crossCategorySearch) {
            specKeys = Set.of("brand", "category");
        } else {
            specKeys = new HashSet<>(specKeyToField.keySet());
            specKeys.add("brand");
        }

        Map<String, List<SpecFacetBucketDto>> facets =
                productAggregationProcessor.extractFacets(searchHits, specKeys);

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

    private String findSingleMatchingCategory(ProductSearchRequest request) {
        NativeQuery probeQuery = productQueryBuilder.buildCategoryDiscoveryQuery(request);
        SearchHits<ProductDocument> probeHits =
                elasticsearchOperations.search(probeQuery, ProductDocument.class);
        Set<String> aggregationKeys = Set.of("category");

        Map<String, List<SpecFacetBucketDto>> categoryFacets =
                productAggregationProcessor.extractFacets(probeHits, aggregationKeys);
        List<SpecFacetBucketDto> buckets = categoryFacets.get("category");
        if (buckets == null || buckets.size() != 1) {
            return null;
        }

        SpecFacetBucketDto singleBucket = buckets.getFirst();
        return singleBucket.getCount() == probeHits.getTotalHits()
                ? singleBucket.getValue()
                : null;
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
