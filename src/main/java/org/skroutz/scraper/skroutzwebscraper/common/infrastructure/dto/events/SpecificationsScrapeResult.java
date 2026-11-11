package org.skroutz.scraper.skroutzwebscraper.common.infrastructure.dto.events;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public record SpecificationsScrapeResult (
       List<SpecificationsScrapeItem> items
) {

    public record SpecificationsScrapeItem (
            Long productId,
            JsonNode rawSpecs,
            JsonNode normalizedSpecs,
            String brand,
            boolean isSuccess
    ) {
    }
}
