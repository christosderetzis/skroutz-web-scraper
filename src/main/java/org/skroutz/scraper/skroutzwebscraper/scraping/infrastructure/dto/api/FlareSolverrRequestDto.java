package org.skroutz.scraper.skroutzwebscraper.scraping.infrastructure.dto.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FlareSolverrRequestDto {

    private String cmd;
    private String url;
    private Integer maxTimeout;
    private String session;
}
