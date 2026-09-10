package org.skroutz.scraper.skroutzwebscraper.priceHistory.application.service;

import lombok.RequiredArgsConstructor;
import org.skroutz.scraper.skroutzwebscraper.priceHistory.domain.repository.PriceHistoryRepository;
import org.skroutz.scraper.skroutzwebscraper.priceHistory.infrastructure.dto.BuyAnalysis;
import org.skroutz.scraper.skroutzwebscraper.priceHistory.infrastructure.dto.PricePoint;
import org.skroutz.scraper.skroutzwebscraper.priceHistory.infrastructure.utils.BestTimeToBuyAnalyzer;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductBuyingAnalysisService {

    private final PriceHistoryRepository priceHistoryRepository;

    private final BestTimeToBuyAnalyzer analyzer;

    public BuyAnalysis analyze(Long productId) {

        Timestamp from =
                Timestamp.from(Instant.now().minus(730, ChronoUnit.DAYS));

        List<PricePoint> history =
                priceHistoryRepository
                        .findPriceHistory(productId, from)
                        .stream()
                        .filter(p -> p.getPrice().compareTo(BigDecimal.ZERO) > 0)
                        .map(p -> new PricePoint(
                                p.getPriceDate().toInstant(),
                                p.getPrice()
                        ))
                        .toList();

        if (history.isEmpty()) {
            throw new IllegalArgumentException(
                    "No price history available"
            );
        }

        BigDecimal currentPrice =
                history.get(history.size() - 1).price();

        return analyzer.analyze(
                history,
                currentPrice
        );
    }
}
