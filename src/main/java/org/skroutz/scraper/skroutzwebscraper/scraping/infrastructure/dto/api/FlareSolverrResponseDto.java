package org.skroutz.scraper.skroutzwebscraper.scraping.infrastructure.dto.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class FlareSolverrResponseDto {

    private String status;
    private String message;
    private String session;
    private Solution solution;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Solution {
        private Integer status;
        private String response;
    }
}
