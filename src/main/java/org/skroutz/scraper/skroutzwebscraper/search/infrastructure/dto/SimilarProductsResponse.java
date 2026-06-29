package org.skroutz.scraper.skroutzwebscraper.search.infrastructure.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SimilarProductsResponse {

    private Long sourceProductId;
    private List<ProductItemDto> products;
    private int totalElements;
}
