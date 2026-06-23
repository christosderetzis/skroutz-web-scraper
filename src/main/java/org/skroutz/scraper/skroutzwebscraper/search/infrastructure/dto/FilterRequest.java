package org.skroutz.scraper.skroutzwebscraper.search.infrastructure.dto;

import lombok.Data;

import java.util.List;

@Data
public class FilterRequest {

    private String key;
    private List<String> values;
    private Double min;
    private Double max;
    private FilterType type;
}
