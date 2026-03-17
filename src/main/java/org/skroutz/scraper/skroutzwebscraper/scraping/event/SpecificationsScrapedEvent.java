package org.skroutz.scraper.skroutzwebscraper.scraping.event;

import com.fasterxml.jackson.databind.JsonNode;

public record SpecificationsScrapedEvent(Long productId, JsonNode specifications) {
}
