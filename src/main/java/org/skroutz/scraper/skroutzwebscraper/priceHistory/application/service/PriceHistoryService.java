package org.skroutz.scraper.skroutzwebscraper.priceHistory.application.service;

import lombok.RequiredArgsConstructor;
import org.skroutz.scraper.skroutzwebscraper.priceHistory.domain.entity.PriceHistory;
import org.skroutz.scraper.skroutzwebscraper.priceHistory.domain.repository.PriceHistoryRepository;
import org.skroutz.scraper.skroutzwebscraper.priceHistory.infrastructure.dto.BuyRecommendationResult;
import org.skroutz.scraper.skroutzwebscraper.priceHistory.infrastructure.dto.PricePoint;
import org.skroutz.scraper.skroutzwebscraper.priceHistory.infrastructure.dto.PriceTrendResult;
import org.skroutz.scraper.skroutzwebscraper.priceHistory.infrastructure.utils.BuyRecommendationCalculator;
import org.skroutz.scraper.skroutzwebscraper.priceHistory.infrastructure.utils.PriceTrendCalculator;
import org.skroutz.scraper.skroutzwebscraper.product.infrastructure.exception.ProductNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PriceHistoryService {

    private final PriceHistoryRepository priceHistoryRepository;
    private final PriceTrendCalculator priceTrendCalculator;
    private final BuyRecommendationCalculator buyRecommendationCalculator;

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

    private List<PricePoint> toPricePoints(List<PriceHistory> productHistories) {
        return productHistories.stream()
                .map(history -> new PricePoint(history.getPriceDate().toLocalDateTime().toLocalDate(), history.getPrice()))
                .toList();
    }
}
