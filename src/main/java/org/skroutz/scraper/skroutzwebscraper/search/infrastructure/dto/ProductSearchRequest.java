package org.skroutz.scraper.skroutzwebscraper.search.infrastructure.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class ProductSearchRequest {

    @NotBlank(message = "Category must not be blank")
    private String category;

    private List<FilterRequest> filters;
    private Double minPrice;
    private Double maxPrice;

    @Min(0)
    private Integer page = 0;

    @Min(1)
    private Integer size = 20;
}
