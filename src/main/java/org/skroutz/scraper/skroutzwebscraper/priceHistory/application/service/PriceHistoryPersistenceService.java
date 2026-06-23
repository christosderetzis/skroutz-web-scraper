package org.skroutz.scraper.skroutzwebscraper.priceHistory.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.skroutz.scraper.skroutzwebscraper.priceHistory.domain.entity.PriceHistory;
import org.skroutz.scraper.skroutzwebscraper.priceHistory.domain.repository.PriceHistoryRepository;
import org.skroutz.scraper.skroutzwebscraper.product.domain.entity.Product;
import org.skroutz.scraper.skroutzwebscraper.product.domain.repository.ProductRepository;
import org.skroutz.scraper.skroutzwebscraper.scraping.infrastructure.dto.events.PriceHistoryScrapeResult;
import org.skroutz.scraper.skroutzwebscraper.common.utils.DateTimeUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PriceHistoryPersistenceService {

    private final ProductRepository productRepository;
    private final PriceHistoryRepository historyRepo;

    @Transactional
    public void saveHistoryResult(PriceHistoryScrapeResult result) {
        Product product = productRepository.findById(result.productId())
                .orElseThrow(() -> new IllegalArgumentException("Product not found ID: " + result.productId()));

        if (result.isSuccess()) {
            // Find last timestamp recorded in DB to filter out incoming duplicates
            Timestamp lastDate = Optional.ofNullable(historyRepo.findTopByProductIdOrderByPriceDateDesc(product.getId()))
                    .map(PriceHistory::getPriceDate)
                    .orElse(new Timestamp(0));

            List<PriceHistory> newRecords = result.historyItems().stream()
                    .map(item -> PriceHistory.builder()
                            .productId(product.getId())
                            .price(item.value())
                            .priceDate(DateTimeUtils.convertEpochToTimestamp(item.timestamp()))
                            .storeName(item.shopName())
                            .build())
                    .filter(record -> record.getPriceDate().after(lastDate))
                    .toList();

            if (!newRecords.isEmpty()) {
                historyRepo.saveAll(newRecords);
                log.info("Saved {} new price history entries for product ID: {}", newRecords.size(), product.getId());
            }

            product.setPriceHistoryParsed(true);
        } else {
            // Optional: Handle retry logic/flag adjustments here on network failure
            log.warn("Skipping DB updates for product ID {} due to previous scrape failure", result.productId());
        }

        productRepository.save(product);
    }
}
