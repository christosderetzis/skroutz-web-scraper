package org.skroutz.scraper.skroutzwebscraper.scraping.event;

import org.skroutz.scraper.skroutzwebscraper.scraping.dto.PriceHistoryResponseDto;

public record PriceHistoryScrapedEvent(Long productId, PriceHistoryResponseDto priceHistory) {
}
