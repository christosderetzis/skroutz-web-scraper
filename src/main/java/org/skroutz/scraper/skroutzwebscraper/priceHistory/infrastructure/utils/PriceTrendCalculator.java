package org.skroutz.scraper.skroutzwebscraper.priceHistory.infrastructure.utils;

import org.skroutz.scraper.skroutzwebscraper.priceHistory.infrastructure.dto.PricePoint;
import org.skroutz.scraper.skroutzwebscraper.priceHistory.infrastructure.dto.PriceTrend;
import org.skroutz.scraper.skroutzwebscraper.priceHistory.infrastructure.dto.PriceTrendResult;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

@Component
public class PriceTrendCalculator {

    private static final int RECENT_DAYS = 7;
    private static final int LOOKBACK_DAYS = 30;

    // Changes smaller than 2% are considered stable.
    private static final BigDecimal STABLE_THRESHOLD = BigDecimal.valueOf(2.0);

    public PriceTrendResult calculateTrend(List<PricePoint> prices) {

        if (prices == null || prices.size() < 8) {
            return new PriceTrendResult(
                    PriceTrend.INSUFFICIENT_DATA,
                    null,
                    null,
                    null,
                    prices == null ? 0 : prices.size()
            );
        }

        LocalDate latestDate = prices.stream()
                .map(PricePoint::date)
                .max(LocalDate::compareTo)
                .orElseThrow();

        LocalDate recentStart =
                latestDate.minusDays(RECENT_DAYS - 1);

        LocalDate previousStart =
                latestDate.minusDays(LOOKBACK_DAYS - 1);

        // prices the last 7 days
        List<PricePoint> recentPrices = prices.stream()
                .filter(p -> !p.date().isBefore(recentStart))
                .toList();

        // prices from 30 days ago to 7 days ago
        List<PricePoint> previousPrices = prices.stream()
                .filter(p ->
                        !p.date().isBefore(previousStart)
                                && p.date().isBefore(recentStart)
                )
                .toList();

        if (recentPrices.isEmpty() || previousPrices.isEmpty()) {
            return new PriceTrendResult(
                    PriceTrend.INSUFFICIENT_DATA,
                    null,
                    null,
                    null,
                    prices.size()
            );
        }

        BigDecimal recentAverage = average(recentPrices);
        BigDecimal previousAverage = average(previousPrices);

        BigDecimal percentageChange = recentAverage
                .subtract(previousAverage)
                .divide(previousAverage, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));

        PriceTrend trend = determineTrend(percentageChange);

        return new PriceTrendResult(
                trend,
                recentAverage,
                previousAverage,
                percentageChange.setScale(2, RoundingMode.HALF_UP),
                prices.size()
        );
    }

    private BigDecimal average(List<PricePoint> prices) {
        BigDecimal sum = prices.stream()
                .map(PricePoint::price)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return sum.divide(
                BigDecimal.valueOf(prices.size()),
                2,
                RoundingMode.HALF_UP
        );
    }

    private PriceTrend determineTrend(BigDecimal percentageChange) {
        if (percentageChange.compareTo(STABLE_THRESHOLD) > 0) {
            return PriceTrend.INCREASING;
        }

        if (percentageChange.compareTo(
                STABLE_THRESHOLD.negate()) < 0) {
            return PriceTrend.DECREASING;
        }

        return PriceTrend.STABLE;
    }
}
