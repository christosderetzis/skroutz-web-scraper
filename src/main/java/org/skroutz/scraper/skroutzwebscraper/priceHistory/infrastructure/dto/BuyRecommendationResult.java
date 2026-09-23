package org.skroutz.scraper.skroutzwebscraper.priceHistory.infrastructure.dto;

import java.math.BigDecimal;

public record BuyRecommendationResult(
        BuyRecommendation recommendation,
        int score,
        BigDecimal currentPrice,
        BigDecimal historicalAverage,
        BigDecimal historicalMinimum,
        BigDecimal percentile,
        BigDecimal percentageBelowAverage,
        BigDecimal percentageAboveMinimum,
        PriceTrend trend,
        int validObservations
) {
}
