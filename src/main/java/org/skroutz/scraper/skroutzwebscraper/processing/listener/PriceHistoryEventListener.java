package org.skroutz.scraper.skroutzwebscraper.processing.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.skroutz.scraper.skroutzwebscraper.processing.entity.PriceHistory;
import org.skroutz.scraper.skroutzwebscraper.processing.entity.Product;
import org.skroutz.scraper.skroutzwebscraper.processing.repository.PriceHistoryRepository;
import org.skroutz.scraper.skroutzwebscraper.processing.repository.ProductRepository;
import org.skroutz.scraper.skroutzwebscraper.processing.utils.DateTimeUtils;
import org.skroutz.scraper.skroutzwebscraper.scraping.dto.PriceHistoryResponseDto;
import org.skroutz.scraper.skroutzwebscraper.scraping.event.PriceHistoryScrapedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.AbstractMap;
import java.util.Collections;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PriceHistoryEventListener {

    private final PriceHistoryRepository priceHistoryRepository;
    private final ProductRepository productRepository;

    @EventListener
    @Transactional
    public void handlePriceHistoryScraped(PriceHistoryScrapedEvent event) {
        log.info("Received PriceHistoryScrapedEvent for product ID: {}", event.productId());

        Product product = productRepository.findById(event.productId())
                .orElseThrow(() -> new IllegalStateException("Product not found: " + event.productId()));

        List<PriceHistory> priceHistories = extractNewPriceHistories(product, event.priceHistory());

        if (!priceHistories.isEmpty()) {
            priceHistoryRepository.saveAll(priceHistories);
            log.info("Saved {} price history records for product ID: {}", priceHistories.size(), product.getId());
        } else {
            log.warn("No new price history data available for product ID: {}", product.getId());
        }

        product.setPriceHistoryParsed(true);
        productRepository.save(product);
    }

    private List<PriceHistory> extractNewPriceHistories(Product product, PriceHistoryResponseDto response) {
        if (response.getMinPrice() == null ||
                response.getMinPrice().getGraphData() == null ||
                response.getMinPrice().getGraphData().getAll() == null ||
                response.getMinPrice().getGraphData().getAll().getValues() == null) {
            return Collections.emptyList();
        }

        PriceHistory lastPriceHistory = priceHistoryRepository
                .findTopByProductIdOrderByPriceDateDesc(product.getId());
        Timestamp lastRecordedDate = lastPriceHistory != null
                ? lastPriceHistory.getPriceDate()
                : new Timestamp(0);

        return response.getMinPrice().getGraphData().getAll().getValues().stream()
                .map(dataPoint -> {
                    Timestamp timestamp = DateTimeUtils.convertEpochToTimestamp(dataPoint.getTimestamp());
                    return new AbstractMap.SimpleEntry<>(timestamp, dataPoint);
                })
                .filter(entry -> entry.getKey().after(lastRecordedDate))
                .map(entry -> PriceHistory.builder()
                        .productId(product.getId())
                        .price(entry.getValue().getValue())
                        .priceDate(entry.getKey())
                        .storeName(entry.getValue().getShopName())
                        .build())
                .toList();
    }
}
