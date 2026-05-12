package org.skroutz.scraper.skroutzwebscraper.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.skroutz.scraper.skroutzwebscraper.dto.PriceHistoryResponseDto;
import org.skroutz.scraper.skroutzwebscraper.entity.PriceHistory;
import org.skroutz.scraper.skroutzwebscraper.entity.Product;
import org.skroutz.scraper.skroutzwebscraper.repository.PriceHistoryRepository;
import org.skroutz.scraper.skroutzwebscraper.repository.ProductRepository;
import org.skroutz.scraper.skroutzwebscraper.scraper.PriceHistoryScraper;
import org.skroutz.scraper.skroutzwebscraper.utils.DateTimeUtils;
import org.skroutz.scraper.skroutzwebscraper.utils.UrlBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class PriceHistoryService {

    private final ProductRepository productRepository;
    private final PriceHistoryRepository historyRepo;
    private final PriceHistoryScraper scraper;
    private final UrlBuilder urlBuilder;
    private final TransactionTemplate transactionTemplate; // Injected by Spring

    @Value("${scraper.delays.price-history-ms:1000}")
    private long delayMs;

    public void fetchPriceHistoryForProducts() {
        log.info("Starting price history scraping task...");

        Slice<Product> productSlice;
        int page = 0;

        do {
            productSlice = productRepository.findAllByPriceHistoryParsed(false, PageRequest.of(page, 100));

            for (Product product : productSlice) {
                // Use transactionTemplate to ensure each product has its own transaction
                transactionTemplate.executeWithoutResult(status -> {
                    processSingleProduct(product);
                });

                takeBreather();
            }
        } while (productSlice.hasNext());
    }

    private void processSingleProduct(Product product) {
        if (product.getUrl() == null || product.getUrl().isBlank()) return;

        try {
            String url = urlBuilder.buildPriceGraphApiUrl(product.getUrl());
            PriceHistoryResponseDto response = scraper.fetchPriceHistory(url);

            List<PriceHistory> newRecords = mapNewHistory(product, response);

            if (!newRecords.isEmpty()) {
                historyRepo.saveAll(newRecords);
            }

            product.setPriceHistoryParsed(true);
            productRepository.save(product); // Use the repository directly here

        } catch (Exception e) {
            log.error("Error processing product {}: {}", product.getId(), e.getMessage());
            // The transactionTemplate will automatically roll back this specific product's changes
        }
    }

    private List<PriceHistory> mapNewHistory(Product product, PriceHistoryResponseDto response) {
        Timestamp lastDate = Optional.ofNullable(historyRepo.findTopByProductIdOrderByPriceDateDesc(product.getId()))
                .map(PriceHistory::getPriceDate)
                .orElse(new Timestamp(0));

        return Optional.ofNullable(response.getMinPrice())
                .map(mp -> mp.getGraphData().getAll().getValues())
                .orElse(Collections.emptyList())
                .stream()
                .filter(data -> DateTimeUtils.convertEpochToTimestamp(data.getTimestamp()).after(lastDate))
                .map(data -> PriceHistory.builder()
                        .productId(product.getId())
                        .price(data.getValue())
                        .priceDate(DateTimeUtils.convertEpochToTimestamp(data.getTimestamp()))
                        .storeName(data.getShopName())
                        .build())
                .toList();
    }

    private void takeBreather() {
        try { Thread.sleep(delayMs); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
