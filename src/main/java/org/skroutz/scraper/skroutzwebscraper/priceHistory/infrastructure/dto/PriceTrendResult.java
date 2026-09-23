package org.skroutz.scraper.skroutzwebscraper.priceHistory.infrastructure.dto;

import java.math.BigDecimal;

public record PriceTrendResult(
        PriceTrend trend,
        BigDecimal recentAverage,
        BigDecimal previousAverage,
        BigDecimal percentageChange,
        int daysAnalyzed
) {
}
