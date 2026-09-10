package org.skroutz.scraper.skroutzwebscraper.priceHistory.infrastructure.utils;

import org.skroutz.scraper.skroutzwebscraper.priceHistory.infrastructure.dto.BuyAnalysis;
import org.skroutz.scraper.skroutzwebscraper.priceHistory.infrastructure.dto.PricePoint;
import org.skroutz.scraper.skroutzwebscraper.priceHistory.infrastructure.enums.BuyRecommendation;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class BestTimeToBuyAnalyzer {

    private static final int TREND_DAYS = 30;
    private static final double DECAY_DAYS = 45.0;
    private static final int BASELINE_DAYS = 90;
    private static final int BASELINE_MIN_POINTS = 7;
    private static final int SEASONAL_OFFSET_1_YEAR = 365;
    private static final int SEASONAL_OFFSET_2_YEARS = 730;
    private static final int SEASONAL_WINDOW_DAYS = 15;
    private static final int SEASONAL_MIN_POINTS = 5;
    private static final int OUTLIER_MIN_POINTS = 20;
    private static final double OUTLIER_MAD_FACTOR = 10.0;
    private static final double FLAT_THRESHOLD = 0.02;
    private static final double NEAR_LOW_FACTOR = 1.05;
    private static final int MARKOV_LOOKBACK_DAYS = 7;
    private static final int MARKOV_OUTCOME_START_DAYS = 14;
    private static final int MARKOV_OUTCOME_END_DAYS = 21;
    private static final double PRICE_DROP_FACTOR = 0.95;
    private static final int MIN_STATE_SAMPLES = 10;
    private static final int MIN_OVERALL_SAMPLES = 30;
    private static final double DEFAULT_PROBABILITY = 0.5;
    private static final int MIN_RECOMMENDATION_DAYS = 30;
    private static final double MIN_RECOMMENDATION_VARIATION = 0.05;
    private static final int MIN_CONFIDENCE_DAYS = 90;
    private static final double MIN_CONFIDENCE_VARIATION = 0.15;
    private static final int MIN_SEASONAL_YEAR_POINTS = 60;

    private static final double PERCENTILE_WEIGHT = 0.30;
    private static final double DISCOUNT_WEIGHT = 0.25;
    private static final double STABILITY_WEIGHT = 0.15;
    private static final double FURTHER_DROP_WEIGHT = 0.15;
    private static final double TREND_WEIGHT = 0.10;
    private static final double LOW_WEIGHT = 0.05;

    public BuyAnalysis analyze(
            List<PricePoint> history,
            BigDecimal currentPrice
    ) {
        if (history == null || history.isEmpty()) {
            throw new IllegalArgumentException("Price history cannot be empty");
        }

        if (currentPrice == null || currentPrice.signum() <= 0) {
            throw new IllegalArgumentException("Current price must be positive");
        }

        Instant now = Instant.now();
        List<PricePoint> cleaned = removeOutliers(toDailySeries(history));

        if (cleaned.isEmpty()) {
            throw new IllegalArgumentException("No valid price history");
        }

        Stats stats = buildStats(cleaned);
        double current = currentPrice.doubleValue();

        double weightedAverage = calculateWeightedAverage(cleaned, now);
        double percentile = calculatePercentile(cleaned, current);

        double baseline = calculateBaseline(cleaned, now);
        double localDiscount = baseline > 0
                ? (baseline - current) / baseline
                : 0;

        Double seasonalBaseline = calculateSeasonalBaseline(cleaned, now);
        Double seasonalDiscount = seasonalBaseline != null && seasonalBaseline > 0
                ? (seasonalBaseline - current) / seasonalBaseline
                : null;

        double discount = seasonalDiscount != null
                ? 0.6 * localDiscount + 0.4 * seasonalDiscount
                : localDiscount;

        double trend = calculateTrend(cleaned, now);
        int flatDays = calculateFlatDays(cleaned, current);
        double daysSinceNewLow = calculateDaysSinceNewLow(cleaned, stats.min());
        double furtherDropProbability = calculateFurtherDropProbability(cleaned, percentile / 100.0);
        Integer seasonalLowMonth = calculateSeasonalLowMonth(cleaned);

        double distanceFromLow = stats.min() > 0
                ? (current - stats.min()) / stats.min()
                : 0;

        double confidence = 100.0
                * Math.min(1.0, cleaned.size() / (double) MIN_CONFIDENCE_DAYS)
                * Math.min(1.0, stats.variation() / MIN_CONFIDENCE_VARIATION);

        double score = calculateScore(
                percentile,
                discount,
                trend,
                flatDays,
                furtherDropProbability,
                current,
                stats.min(),
                stats.p90()
        );

        score = clamp(score, 0, 100);

        BuyRecommendation recommendation = determineRecommendation(
                score,
                trend,
                furtherDropProbability,
                cleaned.size(),
                stats.variation()
        );

        BigDecimal targetPrice =
                BigDecimal.valueOf(stats.p10()).setScale(2, RoundingMode.HALF_UP);

        BigDecimal potentialSavings =
                BigDecimal.valueOf(Math.max(0.0, current - stats.p10()))
                        .setScale(2, RoundingMode.HALF_UP);

        return new BuyAnalysis(
                currentPrice,
                BigDecimal.valueOf(weightedAverage),
                BigDecimal.valueOf(stats.min()),
                BigDecimal.valueOf(stats.max()),
                percentile,
                discount,
                trend,
                distanceFromLow,
                score,
                recommendation,
                BigDecimal.valueOf(baseline),
                seasonalDiscount,
                flatDays,
                daysSinceNewLow,
                furtherDropProbability,
                seasonalLowMonth,
                confidence,
                targetPrice,
                potentialSavings
        );
    }

    private List<PricePoint> toDailySeries(List<PricePoint> history) {
        return history.stream()
                .filter(p -> p != null
                        && p.timestamp() != null
                        && p.price() != null
                        && p.price().signum() > 0)
                .sorted(Comparator.comparing(PricePoint::timestamp))
                .collect(Collectors.toMap(
                        p -> p.timestamp().atOffset(ZoneOffset.UTC).toLocalDate(),
                        p -> p,
                        (existing, replacement) -> replacement,
                        LinkedHashMap::new
                ))
                .values()
                .stream()
                .toList();
    }

    private List<PricePoint> removeOutliers(List<PricePoint> points) {
        if (points.size() < OUTLIER_MIN_POINTS) {
            return points;
        }

        double[] sorted = sortedPrices(points);
        double median = percentile(sorted, 0.5);

        double[] deviations = new double[points.size()];
        for (int i = 0; i < points.size(); i++) {
            deviations[i] =
                    Math.abs(points.get(i).price().doubleValue() - median);
        }
        Arrays.sort(deviations);

        double mad = percentile(deviations, 0.5);
        if (mad <= 0) {
            return points;
        }

        double fence = OUTLIER_MAD_FACTOR * 1.4826 * mad;

        List<PricePoint> filtered = points.stream()
                .filter(p ->
                        Math.abs(p.price().doubleValue() - median) <= fence
                )
                .toList();

        return filtered.size() < OUTLIER_MIN_POINTS ? points : filtered;
    }

    private Stats buildStats(List<PricePoint> points) {
        double[] sorted = sortedPrices(points);

        if (sorted.length == 0) {
            return new Stats(0, 0, 0, 0, 0, 0, 0);
        }

        double min = sorted[0];
        double max = sorted[sorted.length - 1];
        double median = percentile(sorted, 0.5);
        double p10 = percentile(sorted, 0.1);
        double p25 = percentile(sorted, 0.25);
        double p75 = percentile(sorted, 0.75);
        double p90 = percentile(sorted, 0.9);
        double iqr = p75 - p25;
        double variation = median > 0 ? iqr / median : 0;

        return new Stats(
                min,
                max,
                median,
                p10,
                p90,
                iqr,
                variation
        );
    }

    private double[] sortedPrices(List<PricePoint> points) {
        double[] values = new double[points.size()];

        for (int i = 0; i < points.size(); i++) {
            values[i] = points.get(i).price().doubleValue();
        }

        Arrays.sort(values);
        return values;
    }

    private double percentile(double[] sorted, double q) {
        if (sorted.length == 0) {
            return 0;
        }

        if (sorted.length == 1) {
            return sorted[0];
        }

        double rank = clamp(q, 0, 1) * (sorted.length - 1);
        int lower = (int) Math.floor(rank);
        int upper = Math.min(sorted.length - 1, lower + 1);
        double fraction = rank - lower;

        return sorted[lower]
                + (sorted[upper] - sorted[lower]) * fraction;
    }

    private double calculateWeightedAverage(
            List<PricePoint> history,
            Instant now
    ) {
        double weightedSum = 0;
        double weightSum = 0;

        for (PricePoint point : history) {
            double daysAgo =
                    Duration.between(point.timestamp(), now)
                            .toHours() / 24.0;

            double weight =
                    Math.exp(-daysAgo / DECAY_DAYS);

            weightedSum +=
                    point.price().doubleValue() * weight;

            weightSum += weight;
        }

        return weightedSum / weightSum;
    }

    private double calculatePercentile(
            List<PricePoint> history,
            double currentPrice
    ) {
        if (history.isEmpty()) {
            return 0;
        }

        long lowerOrEqual = history.stream()
                .filter(p -> p.price().doubleValue() <= currentPrice)
                .count();

        return ((double) lowerOrEqual / history.size()) * 100.0;
    }

    private double calculateBaseline(
            List<PricePoint> points,
            Instant now
    ) {
        Instant cutoff =
                now.minus(Duration.ofDays(BASELINE_DAYS));

        List<PricePoint> recent = points.stream()
                .filter(p -> !p.timestamp().isBefore(cutoff))
                .toList();

        if (recent.size() >= BASELINE_MIN_POINTS) {
            return median(recent);
        }

        return median(points);
    }

    private double median(List<PricePoint> points) {
        return percentile(sortedPrices(points), 0.5);
    }

    private Double calculateSeasonalBaseline(
            List<PricePoint> points,
            Instant now
    ) {
        List<Double> windowMedians = new ArrayList<>();
        int[] offsets = {
                SEASONAL_OFFSET_1_YEAR,
                SEASONAL_OFFSET_2_YEARS
        };

        for (int offset : offsets) {
            Instant center =
                    now.minus(Duration.ofDays(offset));

            Instant start =
                    center.minus(Duration.ofDays(SEASONAL_WINDOW_DAYS));

            Instant end =
                    center.plus(Duration.ofDays(SEASONAL_WINDOW_DAYS));

            List<PricePoint> window = points.stream()
                    .filter(p ->
                            !p.timestamp().isBefore(start)
                                    && !p.timestamp().isAfter(end)
                    )
                    .toList();

            if (window.size() >= SEASONAL_MIN_POINTS) {
                windowMedians.add(median(window));
            }
        }

        if (windowMedians.isEmpty()) {
            return null;
        }

        double[] values = new double[windowMedians.size()];
        for (int i = 0; i < values.length; i++) {
            values[i] = windowMedians.get(i);
        }

        Arrays.sort(values);
        return percentile(values, 0.5);
    }

    private Integer calculateSeasonalLowMonth(List<PricePoint> points) {
        Map<Integer, List<PricePoint>> byYear = points.stream()
                .collect(Collectors.groupingBy(
                        p -> p.timestamp().atOffset(ZoneOffset.UTC).getYear()
                ));

        List<YearLow> candidates = new ArrayList<>();

        for (Map.Entry<Integer, List<PricePoint>> entry : byYear.entrySet()) {
            if (entry.getValue().size() < MIN_SEASONAL_YEAR_POINTS) {
                continue;
            }

            PricePoint low = entry.getValue().stream()
                    .min(
                            Comparator.comparingDouble(
                                    (PricePoint p) -> p.price().doubleValue()
                            ).thenComparing(PricePoint::timestamp)
                    )
                    .orElseThrow();

            int month =
                    low.timestamp().atOffset(ZoneOffset.UTC).getMonthValue();

            candidates.add(new YearLow(entry.getKey(), month));
        }

        if (candidates.size() < 2) {
            return null;
        }

        candidates.sort(Comparator.comparingInt(YearLow::year));

        for (int i = 0; i < candidates.size() - 1; i++) {
            int current = candidates.get(i).month();
            int next = candidates.get(i + 1).month();

            if (circularMonthDistance(current, next) > 1) {
                return null;
            }
        }

        return candidates.get(candidates.size() - 1).month();
    }

    private int circularMonthDistance(int first, int second) {
        int distance = Math.abs(first - second);
        return Math.min(distance, 12 - distance);
    }

    private double calculateTrend(
            List<PricePoint> history,
            Instant now
    ) {
        Instant cutoff =
                now.minus(Duration.ofDays(TREND_DAYS));

        List<PricePoint> trendHistory = history.stream()
                .filter(p -> !p.timestamp().isBefore(cutoff))
                .toList();

        if (trendHistory.size() < 2) {
            return 0;
        }

        double averageX = 0;
        double averageY = 0;

        double[] x = new double[trendHistory.size()];
        double[] y = new double[trendHistory.size()];

        Instant first = trendHistory.get(0).timestamp();

        for (int i = 0; i < trendHistory.size(); i++) {
            PricePoint point = trendHistory.get(i);

            x[i] =
                    Duration.between(first, point.timestamp())
                            .toHours() / 24.0;

            y[i] = point.price().doubleValue();

            averageX += x[i];
            averageY += y[i];
        }

        averageX /= x.length;
        averageY /= y.length;

        double numerator = 0;
        double denominator = 0;

        for (int i = 0; i < x.length; i++) {
            numerator +=
                    (x[i] - averageX) *
                            (y[i] - averageY);

            denominator +=
                    Math.pow(x[i] - averageX, 2);
        }

        if (denominator == 0 || averageY == 0) {
            return 0;
        }

        double slope = numerator / denominator;

        return slope / averageY;
    }

    private int calculateFlatDays(
            List<PricePoint> points,
            double currentPrice
    ) {
        int flatDays = 0;

        for (int i = points.size() - 1; i >= 0; i--) {
            double price = points.get(i).price().doubleValue();

            if (Math.abs(price - currentPrice)
                    <= FLAT_THRESHOLD * currentPrice) {
                flatDays++;
            } else {
                break;
            }
        }

        return flatDays;
    }

    private double calculateDaysSinceNewLow(
            List<PricePoint> points,
            double minimum
    ) {
        if (points.isEmpty()) {
            return 0;
        }

        Instant last =
                points.get(points.size() - 1).timestamp();

        Instant lastNearLow = null;

        for (PricePoint point : points) {
            if (point.price().doubleValue()
                    <= minimum * NEAR_LOW_FACTOR) {
                lastNearLow = point.timestamp();
            }
        }

        if (lastNearLow == null) {
            return 0;
        }

        return Duration.between(lastNearLow, last)
                .toHours() / 24.0;
    }

    private double calculateFurtherDropProbability(
            List<PricePoint> points,
            double currentBandFraction
    ) {
        int size = points.size();

        if (size < MIN_OVERALL_SAMPLES) {
            return DEFAULT_PROBABILITY;
        }

        int[] stateSamples = new int[9];
        int[] stateDrops = new int[9];
        int totalSamples = 0;
        int totalDrops = 0;

        for (int i = 0; i < size; i++) {
            PricePoint point = points.get(i);

            int refIndex = lastIndexAtOrBefore(
                    points,
                    point.timestamp().minus(
                            Duration.ofDays(MARKOV_LOOKBACK_DAYS)
                    )
            );

            if (refIndex < 0) {
                continue;
            }

            int priorCount = 0;

            for (int j = 0; j < i; j++) {
                if (points.get(j).price().doubleValue()
                        <= point.price().doubleValue()) {
                    priorCount++;
                }
            }

            if (priorCount == 0) {
                continue;
            }

            double priorFraction = (double) priorCount / i;

            double refPrice =
                    points.get(refIndex).price().doubleValue();

            double currentPrice = point.price().doubleValue();

            double change = refPrice > 0
                    ? currentPrice / refPrice - 1.0
                    : 0.0;

            int state =
                    band(priorFraction) * 3 + direction(change);

            int futureIndex = lowerBoundIndex(
                    points,
                    point.timestamp().plus(
                            Duration.ofDays(MARKOV_OUTCOME_START_DAYS)
                    )
            );

            if (futureIndex >= size) {
                continue;
            }

            PricePoint future = points.get(futureIndex);

            if (future.timestamp().isAfter(
                    point.timestamp().plus(
                            Duration.ofDays(MARKOV_OUTCOME_END_DAYS)
                    )
            )) {
                continue;
            }

            boolean drop = future.price().doubleValue()
                    <= currentPrice * PRICE_DROP_FACTOR;

            stateSamples[state]++;
            stateDrops[state] += drop ? 1 : 0;
            totalSamples++;
            totalDrops += drop ? 1 : 0;
        }

        PricePoint last = points.get(size - 1);

        int refIndex = lastIndexAtOrBefore(
                points,
                last.timestamp().minus(
                        Duration.ofDays(MARKOV_LOOKBACK_DAYS)
                )
        );

        if (refIndex < 0) {
            return totalSamples >= MIN_OVERALL_SAMPLES
                    ? (double) totalDrops / totalSamples
                    : DEFAULT_PROBABILITY;
        }

        double refPrice = points.get(refIndex).price().doubleValue();
        double lastPrice = last.price().doubleValue();

        double change = refPrice > 0
                ? lastPrice / refPrice - 1.0
                : 0.0;

        int state =
                band(currentBandFraction) * 3 + direction(change);

        if (stateSamples[state] >= MIN_STATE_SAMPLES) {
            return (double) stateDrops[state] / stateSamples[state];
        }

        if (totalSamples >= MIN_OVERALL_SAMPLES) {
            return (double) totalDrops / totalSamples;
        }

        return DEFAULT_PROBABILITY;
    }

    private int direction(double change) {
        if (change < -0.03) {
            return 2;
        }

        if (change > 0.03) {
            return 1;
        }

        return 0;
    }

    private int band(double fraction) {
        if (fraction < 0.30) {
            return 0;
        }

        if (fraction < 0.70) {
            return 1;
        }

        return 2;
    }

    private int lowerBoundIndex(
            List<PricePoint> points,
            Instant target
    ) {
        int low = 0;
        int high = points.size();

        while (low < high) {
            int mid = (low + high) >>> 1;

            if (points.get(mid).timestamp().isBefore(target)) {
                low = mid + 1;
            } else {
                high = mid;
            }
        }

        return low;
    }

    private int lastIndexAtOrBefore(
            List<PricePoint> points,
            Instant target
    ) {
        int low = 0;
        int high = points.size() - 1;
        int answer = -1;

        while (low <= high) {
            int mid = (low + high) >>> 1;

            if (!points.get(mid).timestamp().isAfter(target)) {
                answer = mid;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }

        return answer;
    }

    private double calculateScore(
            double percentile,
            double discount,
            double trend,
            int flatDays,
            double furtherDropProbability,
            double current,
            double minimum,
            double p90
    ) {
        double percentileScore = 100.0 - percentile;
        double discountScore = normalizeDiscount(discount);
        double trendScore = normalizeTrend(trend);

        double stabilityScore =
                clamp(flatDays / 10.0 * 100.0, 0, 100);

        double furtherDropScore =
                furtherDropProbability * 100.0;

        double lowScore =
                normalizeDistanceFromLow(current, minimum, p90);

        return percentileScore * PERCENTILE_WEIGHT
                + discountScore * DISCOUNT_WEIGHT
                + stabilityScore * STABILITY_WEIGHT
                + furtherDropScore * FURTHER_DROP_WEIGHT
                + trendScore * TREND_WEIGHT
                + lowScore * LOW_WEIGHT;
    }

    private double normalizeDiscount(double discount) {
        double score = 50.0 + (discount * 250.0);
        return clamp(score, 0, 100);
    }

    private double normalizeTrend(double trend) {
        double score = 50.0 - (trend * 10000.0);
        return clamp(score, 0, 100);
    }

    private double normalizeDistanceFromLow(
            double current,
            double minimum,
            double upperBound
    ) {
        if (upperBound <= minimum) {
            return 100;
        }

        double position =
                (current - minimum) /
                        (upperBound - minimum);

        return clamp(
                (1.0 - position) * 100.0,
                0,
                100
        );
    }

    private BuyRecommendation determineRecommendation(
            double score,
            double trend,
            double furtherDropProbability,
            int days,
            double variation
    ) {
        if (days < MIN_RECOMMENDATION_DAYS
                || variation < MIN_RECOMMENDATION_VARIATION) {
            return BuyRecommendation.NEUTRAL;
        }

        if (score >= 80) {
            if (trend < -0.002
                    || furtherDropProbability >= 0.6) {
                return BuyRecommendation.GOOD_TIME;
            }

            return BuyRecommendation.BUY_NOW;
        }

        if (score >= 65) {
            return BuyRecommendation.GOOD_TIME;
        }

        if (score >= 45) {
            return BuyRecommendation.NEUTRAL;
        }

        if (score >= 25) {
            return BuyRecommendation.WAIT;
        }

        return BuyRecommendation.BAD_TIME;
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private record Stats(
            double min,
            double max,
            double median,
            double p10,
            double p90,
            double iqr,
            double variation
    ) {
    }

    private record YearLow(int year, int month) {
    }
}
