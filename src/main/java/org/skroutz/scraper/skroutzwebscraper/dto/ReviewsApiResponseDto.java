package org.skroutz.scraper.skroutzwebscraper.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ReviewsApiResponseDto {

    private ReviewsWrapper reviews;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ReviewsWrapper {
        private List<ReviewDto> reviews;

        @JsonProperty("merged_review_notices")
        private Map<String, String> mergedReviewNotices;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ReviewDto {
        private Long id;
        private Integer rating;

        @JsonProperty("author_name")
        private String authorName;

        @JsonProperty("review_time")
        private String reviewTime;

        @JsonProperty("helpfulness_message")
        private String helpfulnessMessage;

        @JsonProperty("original_formatted_review")
        private String originalFormattedReview;

        @JsonProperty("translated_formatted_review")
        private String translatedFormattedReview;

        @JsonProperty("helpful_votes_count")
        private Integer helpfulVotesCount;

        @JsonProperty("aggregated_review_data")
        private String aggregatedReviewData;

        @JsonProperty("purchased")
        private Boolean verified;
    }
}
