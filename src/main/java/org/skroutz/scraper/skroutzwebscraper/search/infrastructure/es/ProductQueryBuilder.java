package org.skroutz.scraper.skroutzwebscraper.search.infrastructure.es;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import co.elastic.clients.json.JsonData;
import org.apache.commons.lang3.StringUtils;
import org.skroutz.scraper.skroutzwebscraper.search.domain.entity.ProductDocument;
import org.skroutz.scraper.skroutzwebscraper.search.infrastructure.dto.FilterRequest;
import org.skroutz.scraper.skroutzwebscraper.search.infrastructure.dto.ProductSearchRequest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class ProductQueryBuilder {

    public NativeQuery buildSearchQuery(ProductSearchRequest request, String facetCategory, Map<String, String> specKeyToField, int page, int size) {
        Query searchFilters = buildElasticsearchQuery(request);

        NativeQueryBuilder queryBuilder = NativeQuery.builder()
                .withQuery(searchFilters)
                .withPageable(PageRequest.of(page, size))
                .withAggregation("brand", Aggregation.of(a -> a.terms(t -> t.field("brand"))));

        if (StringUtils.isBlank(facetCategory)) {
            queryBuilder.withAggregation("category", Aggregation.of(a -> a.terms(t -> t.field("category"))));
        } else {
            // Dynamically attach terms aggregations
            specKeyToField.forEach((key, field) ->
                    queryBuilder.withAggregation(key, Aggregation.of(a -> a.terms(t -> t.field(field)))));
        }

        return queryBuilder.build();
    }

    public NativeQuery buildCategoryDiscoveryQuery(ProductSearchRequest request) {
        return NativeQuery.builder()
                .withQuery(buildElasticsearchQuery(request))
                .withMaxResults(0)
                .withAggregation("category", Aggregation.of(a -> a.terms(t -> t.field("category"))))
                .build();
    }

    public NativeQuery buildSimilarProductsQuery(Long productId, ProductDocument sourceDoc, int limit) {
        return NativeQuery.builder()
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
    }

    private Query buildElasticsearchQuery(ProductSearchRequest request) {
        List<Query> filters = new ArrayList<>();
        List<Query> mustClauses = new ArrayList<>();

        if (StringUtils.isNotBlank(request.getSearchTerm())) {
            mustClauses.add(buildSearchTermQuery(request.getSearchTerm()));
        }

        if (StringUtils.isNotBlank(request.getCategory())) {
            filters.add(Query.of(q -> q.term(t -> t.field("category").value(request.getCategory()))));
        }

        if (request.getMinPrice() != null || request.getMaxPrice() != null) {
            filters.add(Query.of(q -> q.range(r -> r.untyped(u -> {
                u.field("price");
                if (request.getMinPrice() != null) u.gte(JsonData.of(request.getMinPrice()));
                if (request.getMaxPrice() != null) u.lte(JsonData.of(request.getMaxPrice()));
                return u;
            }))));
        }

        if (request.getFilters() != null) {
            for (FilterRequest filter : request.getFilters()) {
                if (isInvalidFilter(filter)) continue;
                buildDynamicQuery(filter).ifPresent(filters::add);
            }
        }

        return Query.of(q -> q.bool(b -> {
            if (!mustClauses.isEmpty()) {
                b.must(mustClauses);
            }
            b.filter(filters);
            return b;
        }));
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
        List<co.elastic.clients.elasticsearch._types.FieldValue> values = rawValues.stream()
                .filter(Objects::nonNull)
                .map(co.elastic.clients.elasticsearch._types.FieldValue::of)
                .toList();
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

    private Query buildSearchTermQuery(String searchTerm) {
        if (StringUtils.isBlank(searchTerm)) {
            return Query.of(q -> q.matchAll(m -> m));
        }

        return Query.of(q -> q.multiMatch(mm -> mm
                .fields(List.of("title^3", "category^2", "description^1"))
                .query(searchTerm)
                .type(TextQueryType.CrossFields)
                .operator(Operator.And)
        ));
    }
}
