package org.skroutz.scraper.skroutzwebscraper.search.application.service;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.json.JsonData;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregation;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregations;
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

        NativeQuery nativeQuery = NativeQuery.builder()
                .withQuery(Query.of(q -> q
                        .moreLikeThis(mlt -> mlt
                                .fields("title", "description")
                                .like(l -> l.document(d -> d.id(String.valueOf(productId))))
                                .minTermFreq(1)
                                .maxQueryTerms(25)
                                .minDocFreq(2)
                        )
                ))
                .withFilter(Query.of(q -> q.bool(b -> {
                    b.must(m -> m.term(t -> t.field("category").value(FieldValue.of(sourceDoc.getCategory()))));
                    b.mustNot(mn -> mn.term(t -> t.field("id").value(FieldValue.of(productId))));
                    return b;
                })))
                .withMaxResults(limit)
                .build();

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

        // 1. Fetch specifications mapping schema if a category is provided
        Map<String, String> specKeyToField = budgetSpecFieldMap(request.getCategory());

        // 2. Build the unified Native Query (Hits + Aggregations combined)
        NativeQuery nativeQuery = buildNativeQuery(request, specKeyToField, page, size);

        // 3. Execute single Elasticsearch round-trip
        SearchHits<ProductDocument> searchHits =
                elasticsearchOperations.search(nativeQuery, ProductDocument.class);

        // 4. Extract hits and map aggregations
        List<ProductDocument> products = searchHits.getSearchHits().stream()
                .map(SearchHit::getContent)
                .toList();
        List<ProductItemDto> productItemDtos = products.stream()
                .map(productDocumentMapper::toItemDto)
                .toList();

        Map<String, List<SpecFacetBucketDto>> facets = extractFacets(searchHits, specKeyToField.keySet());

        // 5. Build response payload
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

    /**
     * Resolves the Category schema from DB and maps specification keys to their target ES fields.
     */
    private Map<String, String> budgetSpecFieldMap(String category) {
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

    /**
     * Constructs the NativeQuery with search filters and dynamic aggregations.
     */
    private NativeQuery buildNativeQuery(ProductSearchRequest request, Map<String, String> specKeyToField, int page, int size) {
        Query searchFilters = buildElasticsearchQuery(request);

        NativeQueryBuilder queryBuilder = NativeQuery.builder()
                .withQuery(searchFilters)
                .withPageable(PageRequest.of(page, size));

        // Dynamically attach terms aggregations to the exact same query
        specKeyToField.forEach((key, field) ->
                queryBuilder.withAggregation(key, Aggregation.of(a -> a.terms(t -> t.field(field)))));

        return queryBuilder.build();
    }

    /**
     * Safe extraction block for type-agnostic Elasticsearch bucket term results.
     */
    private Map<String, List<SpecFacetBucketDto>> extractFacets(SearchHits<ProductDocument> searchHits, Set<String> aggregationKeys) {
        if (searchHits.getAggregations() == null || aggregationKeys.isEmpty()) {
            return Map.of();
        }

        ElasticsearchAggregations aggregations = (ElasticsearchAggregations) searchHits.getAggregations();
        Map<String, List<SpecFacetBucketDto>> facetsMap = new LinkedHashMap<>();

        for (String key : aggregationKeys) {
            ElasticsearchAggregation agg = aggregations.get(key);
            if (agg == null) continue;

            facetsMap.put(key, mapAggregationToBuckets(agg));
        }

        return facetsMap;
    }

    /**
     * Parses multi-type (String, Long, Double) terms aggregations into the standard SpecFacetBucketDto.
     */
    private List<SpecFacetBucketDto> mapAggregationToBuckets(ElasticsearchAggregation agg) {
        var aggregate = agg.aggregation().getAggregate();

        if (aggregate.isSterms()) {
            return aggregate.sterms().buckets().array().stream()
                    .map(b -> new SpecFacetBucketDto(b.key().stringValue(), b.docCount()))
                    .toList();
        }
        if (aggregate.isLterms()) {
            return aggregate.lterms().buckets().array().stream()
                    .map(b -> new SpecFacetBucketDto(String.valueOf(b.key()), b.docCount()))
                    .toList();
        }
        if (aggregate.isDterms()) {
            return aggregate.dterms().buckets().array().stream()
                    .map(b -> new SpecFacetBucketDto(String.valueOf(b.key()), b.docCount()))
                    .toList();
        }

        return List.of();
    }

    /**
     * Formulates Boolean search filters (Category, Price Range, Specs).
     */
    private Query buildElasticsearchQuery(ProductSearchRequest request) {
        List<Query> filters = new ArrayList<>();

        // Category Filter, always required
        filters.add(Query.of(q -> q.term(t -> t.field("category").value(request.getCategory()))));

        // Global Price Range Filter
        if (request.getMinPrice() != null || request.getMaxPrice() != null) {
            filters.add(Query.of(q -> q.range(r -> r.untyped(u -> {
                u.field("price");
                if (request.getMinPrice() != null) u.gte(JsonData.of(request.getMinPrice()));
                if (request.getMaxPrice() != null) u.lte(JsonData.of(request.getMaxPrice()));
                return u;
            }))));
        }

        // Dynamic Specification Filter Parsing
        if (request.getFilters() != null) {
            for (FilterRequest filter : request.getFilters()) {
                if (isInvalidFilter(filter)) continue;
                buildDynamicQuery(filter).ifPresent(filters::add);
            }
        }

        return Query.of(q -> q.bool(b -> b.filter(filters)));
    }

    private boolean isInvalidFilter(FilterRequest filter) {
        return filter == null || filter.getKey() == null || filter.getKey().isBlank() || filter.getType() == null;
    }

    private Optional<Query> buildDynamicQuery(FilterRequest filter) {
        String baseField = "specifications." + filter.getKey();
        return switch (filter.getType()) {
            case TERM -> createTermQuery(baseField, filter.getValues());
            case RANGE -> createRangeQuery(baseField, filter.getMin(), filter.getMax());
        };
    }

    private Optional<Query> createTermQuery(String field, List<String> rawValues) {
        if (rawValues == null || rawValues.isEmpty()) return Optional.empty();
        List<FieldValue> values = rawValues.stream().filter(Objects::nonNull).map(FieldValue::of).toList();
        if (values.isEmpty()) return Optional.empty();

        Query kwQuery = Query.of(q -> q.terms(t -> t.field(field + ".keyword").terms(tf -> tf.value(values))));
        Query rawQuery = Query.of(q -> q.terms(t -> t.field(field).terms(tf -> tf.value(values))));

        return Optional.of(Query.of(q -> q.bool(b -> b.should(kwQuery).should(rawQuery).minimumShouldMatch("1"))));
    }

    private Optional<Query> createRangeQuery(String field, Object min, Object max) {
        if (min == null && max == null) return Optional.empty();
        return Optional.of(Query.of(q -> q.range(r -> r.untyped(u -> {
            u.field(field);
            if (min != null) u.gte(JsonData.of(min));
            if (max != null) u.lte(JsonData.of(max));
            return u;
        }))));
    }
}




