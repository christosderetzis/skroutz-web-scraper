package org.skroutz.scraper.skroutzwebscraper.service;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.json.JsonData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.skroutz.scraper.skroutzwebscraper.document.ProductDocument;
import org.skroutz.scraper.skroutzwebscraper.dto.search.*;
import org.skroutz.scraper.skroutzwebscraper.dto.ProductSuggestionDto;
import org.skroutz.scraper.skroutzwebscraper.entity.CategorySchema;
import org.skroutz.scraper.skroutzwebscraper.entity.Product;
import org.skroutz.scraper.skroutzwebscraper.mapper.ProductDocumentMapper;
import org.skroutz.scraper.skroutzwebscraper.repository.CategorySchemaRepository;
import org.skroutz.scraper.skroutzwebscraper.repository.ProductElasticsearchRepository;
import org.skroutz.scraper.skroutzwebscraper.schema.FieldType;
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

    private final ProductElasticsearchRepository productElasticsearchRepository;
    private final ProductDocumentMapper productDocumentMapper;
    private final ElasticsearchOperations elasticsearchOperations;
    private final CategorySchemaRepository categorySchemaRepository;

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




