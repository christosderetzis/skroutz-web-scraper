package org.skroutz.scraper.skroutzwebscraper.service;

import lombok.extern.slf4j.Slf4j;
import org.skroutz.scraper.skroutzwebscraper.entity.Product;
import org.skroutz.scraper.skroutzwebscraper.repository.ProductRepository;
import org.skroutz.scraper.skroutzwebscraper.util.UrlBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@Slf4j
public class PriceHistoryService {

    private final PriceHistoryTxService priceHistoryTxService;
    private final ProductRepository productRepository;
    private final UrlBuilder urlBuilder;
    private final long delayMs;

    public PriceHistoryService(PriceHistoryTxService priceHistoryTxService,
                               ProductRepository productRepository,
                               UrlBuilder urlBuilder,
                               @Value("${scraper.delays.price-history-ms:1000}") long delayMs) {
        this.priceHistoryTxService = priceHistoryTxService;
        this.productRepository = productRepository;
        this.urlBuilder = urlBuilder;
        this.delayMs = delayMs;
    }

    public void fetchPriceHistoryForProducts() {
        List<Product> products = productRepository.findAllByPriceHistoryParsed(false);

        for (Product product : products) {
            try {
                if (product.getUrl() == null || product.getUrl().isBlank()) {
                    log.warn("Product URL is empty or null for product ID: {}", product.getId());
                    continue;
                }

                String formattedUrl = urlBuilder.buildPriceGraphApiUrl(product.getUrl());
                priceHistoryTxService.processSingleProduct(product, formattedUrl);

                Thread.sleep(delayMs);
            } catch (Exception e) {
                log.error("Error processing product ID {}: {}", product.getId(), e.getMessage(), e);
            }
        }
    }
}
