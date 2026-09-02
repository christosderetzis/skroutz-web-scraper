package org.skroutz.scraper.skroutzwebscraper.search.infrastructure.dto;

import jakarta.validation.constraints.Min;
import lombok.Data;
import org.skroutz.scraper.skroutzwebscraper.search.infrastructure.dto.validation.SearchQueryRequired;

import java.util.List;

@Data
@SearchQueryRequired
public class ProductSearchRequest {

    private String category;

    private String searchTerm;

    private List<FilterRequest> filters;
    private Double minPrice;
    private Double maxPrice;

    @Min(0)
    private Integer page = 0;

    @Min(1)
    private Integer size = 20;
}
