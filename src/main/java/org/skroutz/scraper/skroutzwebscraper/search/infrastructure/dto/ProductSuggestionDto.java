package org.skroutz.scraper.skroutzwebscraper.search.infrastructure.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ProductSuggestionDto {

    private Long id;
    private String title;
}
