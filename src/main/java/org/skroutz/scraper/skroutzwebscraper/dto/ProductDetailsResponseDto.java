package org.skroutz.scraper.skroutzwebscraper.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ProductDetailsResponseDto {

    private Long id;
    private String url;
    private String title;
    private String imageUrl;
    private String description;
    private BigDecimal price;
    private BigDecimal rating;
    private JsonNode specifications;
}
