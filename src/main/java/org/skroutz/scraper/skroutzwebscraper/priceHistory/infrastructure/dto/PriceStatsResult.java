package org.skroutz.scraper.skroutzwebscraper.priceHistory.infrastructure.dto;

import java.math.BigDecimal;

public record PriceStatsResult(
        BigDecimal currentPrice,
        BigDecimal lowestPrice,
        BigDecimal highestPrice,
        BigDecimal averagePrice,
        BigDecimal medianPrice,
        BigDecimal priceChange30d,
        BigDecimal priceChange90d,
        Integer daysAtLowestPrice
) {
}
