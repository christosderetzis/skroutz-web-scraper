package org.skroutz.scraper.skroutzwebscraper.service;

import lombok.extern.slf4j.Slf4j;
import org.skroutz.scraper.skroutzwebscraper.dto.PriceHistoryResponseDto;
import org.skroutz.scraper.skroutzwebscraper.entity.PriceHistory;
import org.skroutz.scraper.skroutzwebscraper.entity.Product;
import org.skroutz.scraper.skroutzwebscraper.repository.PriceHistoryRepository;
import org.skroutz.scraper.skroutzwebscraper.repository.ProductRepository;
import org.skroutz.scraper.skroutzwebscraper.scraper.PriceHistoryScraper;
import org.skroutz.scraper.skroutzwebscraper.utils.DateTimeUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.AbstractMap;
import java.util.Collections;
import java.util.List;

@Service
@Slf4j
public class PriceHistoryTxService {

    private final PriceHistoryScraper priceHistoryScraper;
    private final PriceHistoryRepository priceHistoryRepository;
    private final ProductRepository productRepository;

    public PriceHistoryTxService(PriceHistoryScraper priceHistoryScraper,
                                 PriceHistoryRepository priceHistoryRepository,
                                 ProductRepository productRepository) {
        this.priceHistoryScraper = priceHistoryScraper;
        this.priceHistoryRepository = priceHistoryRepository;
        this.productRepository = productRepository;
    }

    @Transactional
    public void processSingleProduct(Product product, String url) {
        log.info("Fetching and saving price history for product ID: {}", product.getId());

        PriceHistoryResponseDto response = priceHistoryScraper.fetchPriceHistory(url);

        List<PriceHistory> priceHistories = extractNewPriceHistories(product, response);

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

