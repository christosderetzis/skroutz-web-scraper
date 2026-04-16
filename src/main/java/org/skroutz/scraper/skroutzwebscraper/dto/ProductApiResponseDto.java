package org.skroutz.scraper.skroutzwebscraper.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ProductApiResponseDto {

    @JsonProperty("skus")
    private List<ProductDetailsResponseDto> items;

    @JsonProperty("page")
    private PageDetailsResponseDto page;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class ProductDetailsResponseDto {
        @JsonProperty("id")
        private Long skroutzId;

        @JsonProperty("sku_url")
        private String url;

        @JsonProperty("name")
        private String title;

        @JsonProperty("spec_summary")
        private String description;

        @JsonProperty("price")
        private String price;

        @JsonProperty("image_url")
        private String imageUrl;

        @JsonProperty("review_score")
        private String rating;

        @JsonProperty("reviews_count")
        private String ratingCount;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class PageDetailsResponseDto {
        @JsonProperty("total_pages")
        private Integer totalPages;

        @JsonProperty("current_page")
        private Integer currentPage;
    }
}
