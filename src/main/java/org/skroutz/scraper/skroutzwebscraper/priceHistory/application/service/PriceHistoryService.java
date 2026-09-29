package org.skroutz.scraper.skroutzwebscraper.priceHistory.application.service;

import lombok.RequiredArgsConstructor;
import org.skroutz.scraper.skroutzwebscraper.priceHistory.domain.entity.PriceHistory;
import org.skroutz.scraper.skroutzwebscraper.priceHistory.domain.repository.PriceHistoryRepository;
import org.skroutz.scraper.skroutzwebscraper.priceHistory.infrastructure.dto.BuyRecommendationResult;
import org.skroutz.scraper.skroutzwebscraper.priceHistory.infrastructure.dto.PriceHistoryResult;
import org.skroutz.scraper.skroutzwebscraper.priceHistory.infrastructure.dto.PricePoint;
import org.skroutz.scraper.skroutzwebscraper.priceHistory.infrastructure.dto.PriceStatsResult;
import org.skroutz.scraper.skroutzwebscraper.priceHistory.infrastructure.dto.PriceTrendResult;
import org.skroutz.scraper.skroutzwebscraper.priceHistory.infrastructure.utils.BuyRecommendationCalculator;
import org.skroutz.scraper.skroutzwebscraper.priceHistory.infrastructure.utils.PriceStatsCalculator;
import org.skroutz.scraper.skroutzwebscraper.priceHistory.infrastructure.utils.PriceTrendCalculator;
import org.skroutz.scraper.skroutzwebscraper.product.infrastructure.exception.ProductNotFoundException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PriceHistoryService {

    private final PriceHistoryRepository priceHistoryRepository;
    private final PriceTrendCalculator priceTrendCalculator;
    private final BuyRecommendationCalculator buyRecommendationCalculator;
    private final PriceStatsCalculator priceStatsCalculator;

    public PriceTrendResult calculatePriceTrend(Long productId) {
        List<PriceHistory> productHistories = priceHistoryRepository.findAllByProductId(productId);
        if (productHistories.isEmpty()) {
            throw new ProductNotFoundException(productId);
        }
        return priceTrendCalculator.calculateTrend(toPricePoints(productHistories));
    }

    public BuyRecommendationResult getBuyRecommendation(Long productId) {
        List<PriceHistory> productHistories = priceHistoryRepository.findAllByProductId(productId);
        if (productHistories.isEmpty()) {
            throw new ProductNotFoundException(productId);
        }
        PriceHistory recentItem = priceHistoryRepository.findTopByProductIdOrderByPriceDateDesc(productId);
        return buyRecommendationCalculator.calculate(recentItem.getPrice(), toPricePoints(productHistories));
    }

    public PriceHistoryResult getPriceHistory(Long productId, LocalDate from, LocalDate to) {
        List<PriceHistory> productHistories = priceHistoryRepository.findAllByProductId(productId);
        if (productHistories.isEmpty()) {
            throw new ProductNotFoundException(productId);
        }
        PriceHistory recentItem = priceHistoryRepository.findTopByProductIdOrderByPriceDateDesc(productId);
        List<PricePoint> history = toPricePoints(productHistories).stream()
                .filter(point -> from == null || (point.date().isAfter(from) || point.date().equals(from)))
                .filter(point -> to == null || (point.date().isBefore(to) || point.date().equals(to)))
                .sorted(Comparator.comparing(PricePoint::date))
                .toList();
        return new PriceHistoryResult(productId, recentItem.getPrice(), history);
    }

    public PriceStatsResult getPriceStats(Long productId) {
        List<PriceHistory> productHistories = priceHistoryRepository.findAllByProductId(productId);
        if (productHistories.isEmpty()) {
            throw new ProductNotFoundException(productId);
        }
        PriceHistory recentItem = priceHistoryRepository.findTopByProductIdOrderByPriceDateDesc(productId);
        return priceStatsCalculator.calculate(recentItem.getPrice(), toPricePoints(productHistories));
    }

    private List<PricePoint> toPricePoints(List<PriceHistory> productHistories) {
        return productHistories.stream()
                .map(history -> new PricePoint(history.getPriceDate().toLocalDateTime().toLocalDate(), history.getPrice()))
                .toList();
    }
}
