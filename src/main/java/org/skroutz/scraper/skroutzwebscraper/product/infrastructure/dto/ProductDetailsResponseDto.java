package org.skroutz.scraper.skroutzwebscraper.product.infrastructure.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ProductDetailsResponseDto {

    private Long id;
    private String url;
    private String title;
    private String brand;
    private String category;
    private String imageUrl;
    private String description;
    private BigDecimal price;
    private BigDecimal rating;
    private Map<String, Object> specifications;
}
