package org.skroutz.scraper.skroutzwebscraper.priceHistory.infrastructure.dto;

import org.skroutz.scraper.skroutzwebscraper.priceHistory.infrastructure.enums.BuyRecommendation;

import java.math.BigDecimal;

public record BuyAnalysis(
        BigDecimal currentPrice,
        BigDecimal weightedAveragePrice,
        BigDecimal historicalMinimum,
        BigDecimal historicalMaximum,
        double percentile,
        double discount,
        double trend,
        double distanceFromLow,
        double score,
        BuyRecommendation recommendation,
        BigDecimal baselinePrice,
        Double seasonalDiscount,
        int flatDays,
        double daysSinceNewLow,
        double furtherDropProbability,
        Integer seasonalLowMonth,
        double confidence,
        BigDecimal targetPrice,
        BigDecimal potentialSavings
) {
}
