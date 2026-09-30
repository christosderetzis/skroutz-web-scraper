package org.skroutz.scraper.skroutzwebscraper.priceHistory.infrastructure.dto;

import java.math.BigDecimal;
import java.util.List;

public record PriceHistoryResult(
        Long productId,
        BigDecimal currentPrice,
        List<PricePoint> history
) {
}
