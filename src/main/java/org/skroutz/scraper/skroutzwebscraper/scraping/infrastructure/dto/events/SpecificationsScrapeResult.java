package org.skroutz.scraper.skroutzwebscraper.scraping.infrastructure.dto.events;

import com.fasterxml.jackson.databind.JsonNode;

public record SpecificationsScrapeResult (
        Long productId,
        JsonNode rawSpecs,
        JsonNode normalizedSpecs,
        String brand,
        boolean isSuccess
) { }
