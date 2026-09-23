package org.skroutz.scraper.skroutzwebscraper.priceHistory.infrastructure.utils;

import lombok.RequiredArgsConstructor;
import org.skroutz.scraper.skroutzwebscraper.priceHistory.infrastructure.dto.*;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;

@Component
@RequiredArgsConstructor
public class BuyRecommendationCalculator {

    private final PriceTrendCalculator priceTrendCalculator;

    private static final int MIN_OBSERVATIONS = 30;

    /*
        * Calculate the buy recommendation based on the current price and the price history.
        * @param currentPrice The current price of the product.
        * @param priceHistory The price history of the product.
        * @return The buy recommendation.
        *
        * 4 factors are considered:
        * 1. Momentum: The percentile of the current price from the prices who are lower than or equal to it. --> 40% of the score
        * 2. Current price vs historical average: The percentage of the current price that is below the average. --> 25% of the score
        * 3. Current price vs historical minimum: The percentage of the current price that is below the minimum. --> 20% of the score
        * 4. Price trend: The trend of the price over the last 30 days. --> 15% of the score
     */
    public BuyRecommendationResult calculate(
            BigDecimal currentPrice,
            List<PricePoint> priceHistory
    ) {

        List<PricePoint> validPrices = priceHistory.stream()
                .filter(p -> p.price() != null)
                .filter(p -> p.price().compareTo(BigDecimal.ZERO) > 0)
                .sorted(Comparator.comparing(PricePoint::date))
                .toList();

        if (validPrices.size() < MIN_OBSERVATIONS) {
            return insufficientData(currentPrice, validPrices.size());
        }

        // find the average price (totalPriceSum / numberOfPrices)
        BigDecimal average = calculateAverage(validPrices);

        // find the lowest price
        BigDecimal minimum = validPrices.stream()
                .map(PricePoint::price)
                .min(BigDecimal::compareTo)
                .orElseThrow();

        // find the percentile of the current price from the prices who are lower than or equal to it
        // percentile = (number of prices lower than or equal to the current price) / total number of prices
        BigDecimal percentile = calculatePercentile(
                currentPrice,
                validPrices
        );

        // (average - currentPrice) / average = Current price vs historical average
        BigDecimal belowAverage = percentage(
                average.subtract(currentPrice),
                average
        );

        // (currentPrice - minimum) / minimum = Current price vs historical minimum
        BigDecimal aboveMinimum = percentage(
                currentPrice.subtract(minimum),
                minimum
        );

        // calculate the trend if (avg7days - avg30days)/avg30days * 100 > 0 --> INCREASING, < 0 --> DECREASING, = 0 --> STABLE
        PriceTrendResult trend = calculateTrend(validPrices);

        int score = 0;

        // 40% --> momentum
        score += percentileScore(percentile);

        // 25% --> Current price vs historical average
        score += averageScore(belowAverage);

        // 20% --> Current price vs historical minimum
        score += minimumScore(aboveMinimum);

        // 15% --> price trend
        score += trendScore(trend);

        BuyRecommendation recommendation =
                recommendation(score);

        return new BuyRecommendationResult(
                recommendation,
                score,
                currentPrice,
                average,
                minimum,
                percentile,
                belowAverage,
                aboveMinimum,
                trend.trend(),
                validPrices.size()
        );
    }

    private BigDecimal calculateAverage(
            List<PricePoint> prices
    ) {
        BigDecimal sum = prices.stream()
                .map(PricePoint::price)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // average is: totalPriceSum / numberOfPrices
        return sum.divide(
                BigDecimal.valueOf(prices.size()),
                2,
                RoundingMode.HALF_UP
        );
    }

    private BigDecimal calculatePercentile(
            BigDecimal currentPrice,
            List<PricePoint> prices
    ) {
        long cheaperOrEqual = prices.stream()
                .filter(p ->
                        p.price().compareTo(currentPrice) <= 0
                )
                .count();

        return BigDecimal.valueOf(cheaperOrEqual)
                .divide(
                        BigDecimal.valueOf(prices.size()),
                        4,
                        RoundingMode.HALF_UP
                )
                .multiply(BigDecimal.valueOf(100));
    }

    private BigDecimal percentage(
            BigDecimal difference,
            BigDecimal base
    ) {
        return difference
                .divide(base, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }

    private int percentileScore(BigDecimal percentile) {
        double value = percentile.doubleValue();

        if (value <= 10) {
            return 40;
        }

        if (value <= 25) {
            return 35;
        }

        if (value <= 40) {
            return 28;
        }

        if (value <= 60) {
            return 18;
        }

        if (value <= 75) {
            return 10;
        }

        return 0;
    }

    private int averageScore(BigDecimal belowAverage) {
        double value = belowAverage.doubleValue();

        if (value >= 15) {
            return 25;
        }

        if (value >= 10) {
            return 22;
        }

        if (value >= 5) {
            return 17;
        }

        if (value >= 0) {
            return 10;
        }

        if (value >= -5) {
            return 5;
        }

        return 0;
    }

    private int minimumScore(BigDecimal aboveMinimum) {
        double value = aboveMinimum.doubleValue();

        if (value <= 2) {
            return 20;
        }

        if (value <= 5) {
            return 17;
        }

        if (value <= 10) {
            return 13;
        }

        if (value <= 20) {
            return 7;
        }

        return 0;
    }

    private int trendScore(PriceTrendResult trend) {
        return switch (trend.trend()) {
            case DECREASING -> 15;
            case STABLE -> 8;
            case INCREASING -> 0;
            case INSUFFICIENT_DATA -> 0;
        };
    }

    private BuyRecommendation recommendation(int score) {
        if (score >= 80) {
            return BuyRecommendation.BUY_NOW;
        }

        if (score >= 65) {
            return BuyRecommendation.GOOD_TIME_TO_BUY;
        }

        if (score >= 45) {
            return BuyRecommendation.NEUTRAL;
        }

        if (score >= 25) {
            return BuyRecommendation.WAIT;
        }

        return BuyRecommendation.STRONGLY_WAIT;
    }

    private BuyRecommendationResult insufficientData(
            BigDecimal currentPrice,
            int observations
    ) {
        return new BuyRecommendationResult(
                BuyRecommendation.INSUFFICIENT_DATA,
                0,
                currentPrice,
                null,
                null,
                null,
                null,
                null,
                PriceTrend.INSUFFICIENT_DATA,
                observations
        );
    }

    private PriceTrendResult calculateTrend(
            List<PricePoint> prices
    ) {
        // Fixed: Method name changed from .calculate() to .calculateTrend()
        // to match your PriceTrendService class.
        return priceTrendCalculator.calculateTrend(prices);
    }
}
