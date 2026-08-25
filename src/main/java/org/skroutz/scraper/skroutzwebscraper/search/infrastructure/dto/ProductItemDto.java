package org.skroutz.scraper.skroutzwebscraper.search.infrastructure.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ProductItemDto {

    private Long id;
    private String url;
    private String title;
    private String category;
    private String brand;
    private String imageUrl;
    private BigDecimal price;
    private String description;
    private BigDecimal rating;
}
