package org.skroutz.scraper.skroutzwebscraper.common.infrastructure.dto.events;

import org.skroutz.scraper.skroutzwebscraper.common.infrastructure.dto.api.ProductApiResponseDto;


public record ProductsScrapeResult(
        ProductApiResponseDto productApiResponseDto,
        String category
) {
}
