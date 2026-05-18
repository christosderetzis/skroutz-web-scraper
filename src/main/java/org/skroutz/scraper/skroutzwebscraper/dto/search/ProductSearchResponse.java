package org.skroutz.scraper.skroutzwebscraper.dto.search;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.skroutz.scraper.skroutzwebscraper.document.ProductDocument;

import java.util.List;
import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ProductSearchResponse {

    private List<ProductItemDto> products;
    private Map<String, List<SpecFacetBucketDto>> filters;
    private long totalElements;
    private int page;
    private int size;
    private int totalPages;
}
