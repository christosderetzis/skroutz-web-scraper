package org.skroutz.scraper.skroutzwebscraper.common.infrastructure.dto.events;

import org.skroutz.scraper.skroutzwebscraper.common.infrastructure.dto.api.ProductApiResponseDto;
import org.springframework.modulith.NamedInterface;


@NamedInterface(name = "ProductsScrapeResult")
public record ProductsScrapeResult(
        ProductApiResponseDto productApiResponseDto,
        String category
) {
}
