package org.skroutz.scraper.skroutzwebscraper.priceHistory.infrastructure.utils;

import org.skroutz.scraper.skroutzwebscraper.priceHistory.infrastructure.dto.PricePoint;
import org.skroutz.scraper.skroutzwebscraper.priceHistory.infrastructure.dto.PriceStatsResult;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

@Component
public class PriceStatsCalculator {

    private static final int CHANGE_30D_DAYS = 30;
    private static final int CHANGE_90D_DAYS = 90;

    public PriceStatsResult calculate(BigDecimal currentPrice, List<PricePoint> priceHistory) {

        List<PricePoint> validPrices = priceHistory.stream()
                .filter(p -> p.price() != null)
                .filter(p -> p.price().compareTo(BigDecimal.ZERO) > 0)
                .sorted(Comparator.comparing(PricePoint::date))
                .toList();

        if (validPrices.isEmpty()) {
            return new PriceStatsResult(
                    currentPrice,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    0
            );
        }

        BigDecimal lowestPrice = validPrices.stream()
                .map(PricePoint::price)
                .min(BigDecimal::compareTo)
                .orElseThrow();

        BigDecimal highestPrice = validPrices.stream()
                .map(PricePoint::price)
                .max(BigDecimal::compareTo)
                .orElseThrow();

        BigDecimal averagePrice = calculateAverage(validPrices);

        BigDecimal medianPrice = calculateMedian(validPrices);

        int daysAtLowestPrice = (int) validPrices.stream()
                .filter(p -> p.price().compareTo(lowestPrice) == 0)
                .count();

        BigDecimal priceChange30d = calculateChange(currentPrice, validPrices, CHANGE_30D_DAYS);

        BigDecimal priceChange90d = calculateChange(currentPrice, validPrices, CHANGE_90D_DAYS);

        return new PriceStatsResult(
                currentPrice,
                lowestPrice,
                highestPrice,
                averagePrice,
                medianPrice,
                priceChange30d,
                priceChange90d,
                daysAtLowestPrice
        );
    }

    private BigDecimal calculateAverage(List<PricePoint> prices) {
        BigDecimal sum = prices.stream()
                .map(PricePoint::price)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return sum.divide(
                BigDecimal.valueOf(prices.size()),
                2,
                RoundingMode.HALF_UP
        );
    }

    private BigDecimal calculateMedian(List<PricePoint> prices) {
        List<BigDecimal> sortedPrices = prices.stream()
                .map(PricePoint::price)
                .sorted()
                .toList();

        int size = sortedPrices.size();
        int mid = size / 2;

        if (size % 2 == 1) {
            return sortedPrices.get(mid).setScale(2, RoundingMode.HALF_UP);
        }

        BigDecimal lower = sortedPrices.get(mid - 1);
        BigDecimal upper = sortedPrices.get(mid);

        return lower.add(upper)
                .divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateChange(
            BigDecimal currentPrice,
            List<PricePoint> prices,
            int windowDays
    ) {
        LocalDate latestDate = prices.getLast().date();
        LocalDate windowStart = latestDate.minusDays(windowDays);

        PricePoint base = prices.stream()
                .filter(p -> p.date().isAfter(windowStart))
                .findFirst()
                .orElseThrow();

        return percentage(currentPrice.subtract(base.price()), base.price());
    }

    private BigDecimal percentage(BigDecimal difference, BigDecimal base) {
        return difference
                .divide(base, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }
}
