package org.skroutz.scraper.skroutzwebscraper.service;

import lombok.extern.slf4j.Slf4j;
import org.skroutz.scraper.skroutzwebscraper.entity.Product;
import org.skroutz.scraper.skroutzwebscraper.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@Slf4j
public class PriceHistoryService {

    private final PriceHistoryTxService priceHistoryTxService;
    private final ProductRepository productRepository;

    // Configurable delay between products
    @Value("${price-history.delay-ms:1000}")
    private long delayMs;

    public PriceHistoryService(PriceHistoryTxService priceHistoryTxService,
                                  ProductRepository productRepository) {
        this.priceHistoryTxService = priceHistoryTxService;
        this.productRepository = productRepository;
    }

    public void fetchPriceHistoryForProducts() {
        List<Product> products = productRepository.findAllByPriceHistoryParsed(false);

        for (Product product : products) {
            try {
                if (product.getUrl() == null || product.getUrl().isBlank()) {
                    log.warn("Product URL is empty or null for product ID: {}", product.getId());
                    continue;
                }

                String formattedUrl = buildPriceGraphUrl(product.getUrl());
                priceHistoryTxService.processSingleProduct(product, formattedUrl);

                Thread.sleep(delayMs);
            } catch (Exception e) {
                log.error("Error processing product ID {}: {}", product.getId(), e.getMessage(), e);
            }
        }
    }

    private String buildPriceGraphUrl(String productUrl) {
        int htmlIndex = productUrl.indexOf(".html");
        if (htmlIndex != -1) {
            productUrl = productUrl.substring(0, htmlIndex);
        }
        return productUrl + "/price_graph.json?shipping_country=GR&currency=EUR";
    }
}
