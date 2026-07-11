package org.skroutz.scraper.skroutzwebscraper.search.infrastructure.es;

import org.skroutz.scraper.skroutzwebscraper.search.infrastructure.dto.SpecFacetBucketDto;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregation;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchAggregations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class ProductAggregationProcessor {

    @SuppressWarnings("unchecked")
    public Map<String, List<SpecFacetBucketDto>> extractFacets(SearchHits<?> searchHits, Set<String> aggregationKeys) {
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
}
