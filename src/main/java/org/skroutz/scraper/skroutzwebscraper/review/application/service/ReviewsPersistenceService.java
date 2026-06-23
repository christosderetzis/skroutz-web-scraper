package org.skroutz.scraper.skroutzwebscraper.review.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.skroutz.scraper.skroutzwebscraper.review.domain.entity.Review;
import org.skroutz.scraper.skroutzwebscraper.review.domain.repository.ReviewRepository;
import org.skroutz.scraper.skroutzwebscraper.review.infrastructure.mapper.ReviewsMapper;
import org.skroutz.scraper.skroutzwebscraper.scraping.infrastructure.dto.events.ReviewsScrapeResult;
import org.skroutz.scraper.skroutzwebscraper.product.domain.entity.Product;
import org.skroutz.scraper.skroutzwebscraper.product.domain.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewsPersistenceService {

    private final ReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final ReviewsMapper reviewsMapper;

    @Transactional
    public void saveReviewsResult(ReviewsScrapeResult result) {
        Product product = productRepository.findById(result.productId())
                .orElseThrow(() -> new IllegalArgumentException("Product target entity missing ID: " + result.productId()));

        if (result.isSuccess()) {
            List<Review> reviews = reviewsMapper.mapToReviews(result.reviewDtos());

            if (reviews != null && !reviews.isEmpty()) {
                reviews.forEach(r -> {
                    r.setProductId(product.getId());
                    r.setProduct(product);
                });
                reviewRepository.saveAll(reviews);
                log.info("Saved {} reviews to DB for product ID: {}", reviews.size(), product.getId());
            } else {
                log.warn("No reviews payload found to convert for product ID: {}", product.getId());
            }

            product.setReviewsParsed(true);
        } else {
            log.warn("Scraping failed for product ID: {}. Skipping review database entry.", product.getId());
            // Optional: Set a retry counter limit or flag adjustment here if needed
        }

        productRepository.save(product);
    }
}
