package org.skroutz.scraper.skroutzwebscraper.common.infrastructure.dto.events;

import org.springframework.modulith.NamedInterface;

import java.math.BigDecimal;
import java.util.List;

@NamedInterface(name = "PriceHistoryScrapeResult")
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
