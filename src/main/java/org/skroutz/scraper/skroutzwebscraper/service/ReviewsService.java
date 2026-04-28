package org.skroutz.scraper.skroutzwebscraper.service;

import lombok.extern.slf4j.Slf4j;
import org.skroutz.scraper.skroutzwebscraper.agent.ReviewSummarizer;
import org.skroutz.scraper.skroutzwebscraper.dto.PartialSummary;
import org.skroutz.scraper.skroutzwebscraper.entity.Product;
import org.skroutz.scraper.skroutzwebscraper.entity.Review;
import org.skroutz.scraper.skroutzwebscraper.repository.ProductRepository;
import org.skroutz.scraper.skroutzwebscraper.repository.ReviewRepository;
import org.skroutz.scraper.skroutzwebscraper.utils.ReviewChunker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class ReviewsService {

    private final ReviewsTxService reviewsTxService;
    private final ProductRepository productRepository;
    private final ReviewRepository reviewRepository;
    private final ReviewSummarizer reviewSummarizer;
    private final long delayMs;

    public static final int CHUNK_SIZE = 6000; // Adjust based on model limits and expected review lengths

    public ReviewsService(ReviewsTxService reviewsTxService,
                          ProductRepository productRepository,
                          ReviewRepository reviewRepository,
                          ReviewSummarizer reviewSummarizer,
                          @Value("${scraper.delays.reviews-ms:2000}") long delayMs) {
        this.reviewsTxService = reviewsTxService;
        this.productRepository = productRepository;
        this.reviewRepository = reviewRepository;
        this.reviewSummarizer = reviewSummarizer;
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

    public void summarizeReviews(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product not found with ID: " + productId));

        if (product.getReviewsParsed() == null || !product.getReviewsParsed()) {
            log.warn("Cannot summarize reviews for product ID {} because reviews have not been parsed yet.", productId);
            return;
        }

        List<String> reviewTexts = reviewRepository.findAllByProductIdAndReviewTextIsNotNull(productId)
                .stream()
                .map(Review::getReviewText)
                .toList();

        if (reviewTexts.isEmpty()) {
            log.info("No reviews with text found for product ID {}. Skipping summarization.", productId);
            return;
        }

        List<String> chunks = ReviewChunker.chunkByCharSize(reviewTexts, CHUNK_SIZE);

        List<String> partials = chunks.stream()
                .map(reviewSummarizer::summarize)
                .map(PartialSummary::summary)
                .toList();

        String finalInput = String.join("\n", partials);

        PartialSummary partialSummary = reviewSummarizer.summarize(finalInput);

        String finalSummary = partialSummary.summary();
        log.info("Summarized reviews for product ID {}: {}", productId, finalSummary);
    }

}
