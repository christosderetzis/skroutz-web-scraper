package org.skroutz.scraper.skroutzwebscraper.service;

import lombok.extern.slf4j.Slf4j;
import org.skroutz.scraper.skroutzwebscraper.entity.Product;
import org.skroutz.scraper.skroutzwebscraper.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class ReviewsService {

    private final ReviewsTxService reviewsTxService;
    private final ProductRepository productRepository;
    private final long delayMs;

    public ReviewsService(ReviewsTxService reviewsTxService,
                          ProductRepository productRepository,
                          @Value("${scraper.delays.reviews-ms:2000}") long delayMs) {
        this.reviewsTxService = reviewsTxService;
        this.productRepository = productRepository;
        this.delayMs = delayMs;
    }

    public void parseReviews() {
        List<Product> products = productRepository.findAllByReviewsParsedAndRatingIsNotNull(false);

        for (Product product : products) {
            try {
                if (product.getUrl() == null || product.getUrl().isBlank()) {
                    log.warn("Product URL is empty or null for product ID: {}", product.getId());
                    continue;
                }

                reviewsTxService.processSingleProduct(product);

                Thread.sleep(delayMs);
            } catch (Exception e) {
                log.error("Error processing reviews for product ID {}: {}", product.getId(), e.getMessage(), e);
            }
        }
    }

}
