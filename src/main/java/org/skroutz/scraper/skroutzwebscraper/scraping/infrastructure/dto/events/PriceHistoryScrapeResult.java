package org.skroutz.scraper.skroutzwebscraper.scraping.infrastructure.dto.events;

import java.math.BigDecimal;
import java.util.List;

public record PriceHistoryScrapeResult(
        Long productId,
        List<PriceHistoryItem> historyItems,
        boolean isSuccess
) {
    public record PriceHistoryItem(
            BigDecimal value,
            Long timestamp,
            String shopName
    ) {}
}
