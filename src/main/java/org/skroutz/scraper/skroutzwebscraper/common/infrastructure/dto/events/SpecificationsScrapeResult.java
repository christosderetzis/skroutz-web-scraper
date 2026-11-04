package org.skroutz.scraper.skroutzwebscraper.common.infrastructure.dto.events;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.modulith.NamedInterface;

import java.util.List;

@NamedInterface(name = "SpecificationsScrapeResult")
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
