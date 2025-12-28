package org.skroutz.scraper.skroutzwebscraper.service;

import lombok.extern.slf4j.Slf4j;
import org.skroutz.scraper.skroutzwebscraper.dto.pricehistory.PriceHistoryResponseDto;
import org.skroutz.scraper.skroutzwebscraper.entity.PriceHistory;
import org.skroutz.scraper.skroutzwebscraper.entity.Product;
import org.skroutz.scraper.skroutzwebscraper.repository.PriceHistoryRepository;
import org.skroutz.scraper.skroutzwebscraper.repository.ProductRepository;
import org.skroutz.scraper.skroutzwebscraper.scraper.PriceHistoryScraper;
import org.skroutz.scraper.skroutzwebscraper.utils.DateTimeUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class PriceHistoryService {

    private final PriceHistoryScraper priceHistoryScraper;
    private final PriceHistoryRepository priceHistoryRepository;
    private final ProductRepository productRepository;
    private final PriceHistoryService self;

    public PriceHistoryService(
            PriceHistoryScraper priceHistoryScraper,
            PriceHistoryRepository priceHistoryRepository,
            ProductRepository productRepository,
            @Lazy PriceHistoryService self) {
        this.priceHistoryScraper = priceHistoryScraper;
        this.priceHistoryRepository = priceHistoryRepository;
        this.productRepository = productRepository;
        this.self = self;
    }

    public void fetchPriceHistoryForProducts() {
        List<Product> products = productRepository.findAllByPriceHistoryParsed(false);

        for (Product product : products) {
            try {
                String url = product.getUrl();
                if (url != null && !url.isBlank()) {
                    String formattedUrl = buildPriceGraphUrl(url);
                    self.savePriceHistoryForProduct(formattedUrl, product);
                    Thread.sleep(1000);
                } else {
                    log.warn("Product URL is empty or null for product ID: {}", product.getId());
                }
            } catch (Exception e) {
                log.error("Error fetching price history for product ID {}: {}", product.getId(), e.getMessage(), e);
            }
        }
    }

    @Transactional
    public void savePriceHistoryForProduct(String url, Product product) {
        log.info("Fetching and saving price history for product ID: {}", product.getId());

        PriceHistoryResponseDto response = priceHistoryScraper.fetchPriceHistory(url);

        if (response.getMinPrice() != null &&
                response.getMinPrice().getGraphData() != null &&
                response.getMinPrice().getGraphData().getAll() != null &&
                response.getMinPrice().getGraphData().getAll().getValues() != null) {

            List<PriceHistory> priceHistories = new ArrayList<>();

            response.getMinPrice().getGraphData().getAll().getValues().forEach(dataPoint -> {
                PriceHistory priceHistory = PriceHistory.builder()
                        .productId(product.getId())
                        .price(dataPoint.getValue())
                        .priceDate(DateTimeUtils.convertTimestampToLocalDateTime(dataPoint.getTimestamp()))
                        .storeName(dataPoint.getShopName())
                        .build();

                priceHistories.add(priceHistory);
            });

            priceHistoryRepository.saveAll(priceHistories);
            log.info("Successfully saved {} price history records for product ID: {}", priceHistories.size(), product.getId());
        } else {
            log.warn("No price history data available to save for product ID: {}", product.getId());
        }

        product.setPriceHistoryParsed(true);
        productRepository.save(product);
    }

    private String buildPriceGraphUrl(String productUrl) {
        // Remove .html and everything after it (including query parameters)
        int htmlIndex = productUrl.indexOf(".html");
        if (htmlIndex != -1) {
            productUrl = productUrl.substring(0, htmlIndex);
        }
        return productUrl + "/price_graph.json?shipping_country=GR&currency=EUR";
    }
}
