package org.skroutz.scraper.skroutzwebscraper.processing.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ScraperRequestDto {

    private String url;
}
